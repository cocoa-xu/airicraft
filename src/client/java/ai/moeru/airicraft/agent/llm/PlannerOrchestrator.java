package ai.moeru.airicraft.agent.llm;

import ai.moeru.airicraft.Airicraft;
import ai.moeru.airicraft.BridgeUnavailableException;
import ai.moeru.airicraft.agent.dialogue.DialogueTurn;
import ai.moeru.airicraft.agent.events.SemanticEvent;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public final class PlannerOrchestrator {
	private final PlannerExecutor plannerExecutor;
	private final PlannerCompactionService compactionService;
	private final PlannerContextAggregator contextAggregator;
	private final CurrentViewVisionTool visionTool;

	private PlannerRequest baseRequest;
	private boolean toolUsed;
	private CompletableFuture<String> toolResultFuture;
	private CompactionExecutionResult lastCompactionResult;

	public PlannerOrchestrator(
		PlannerExecutor plannerExecutor,
		PlannerCompactionService compactionService,
		PlannerContextAggregator contextAggregator,
		CurrentViewVisionTool visionTool
	) {
		this.plannerExecutor = Objects.requireNonNull(plannerExecutor, "plannerExecutor");
		this.compactionService = Objects.requireNonNull(compactionService, "compactionService");
		this.contextAggregator = Objects.requireNonNull(contextAggregator, "contextAggregator");
		this.visionTool = Objects.requireNonNull(visionTool, "visionTool");
	}

	public boolean isConfigured() {
		return plannerExecutor.isConfigured();
	}

	public boolean hasInFlight() {
		return plannerExecutor.hasInFlight() || compactionService.hasInFlight() || toolResultFuture != null;
	}

	public PlannerOrchestratorDebugSnapshot debugSnapshot() {
		return new PlannerOrchestratorDebugSnapshot(
			isConfigured(),
			hasInFlight(),
			plannerExecutor.hasInFlight(),
			compactionService.hasInFlight(),
			toolResultFuture != null,
			toolUsed,
			baseRequest,
			lastCompactionResult,
			contextAggregator.debugSnapshot()
		);
	}

	public boolean submit(PlannerRequest request) {
		Objects.requireNonNull(request, "request");
		if (hasInFlight()) {
			return false;
		}

		baseRequest = request;
		toolUsed = false;
		if (contextAggregator.compactionPending()) {
			return compactionService.submit(contextAggregator.buildCompactionConversation());
		}
		return submitPlannerConversation(request);
	}

	public PlannerExecutionResult poll() {
		if (compactionService.hasInFlight()) {
			CompactionExecutionResult compactionResult = compactionService.poll();
			if (compactionResult == null) {
				return null;
			}
			completeCompaction(compactionResult);
			if (baseRequest == null) {
				clearState();
				return null;
			}
			if (!submitPlannerConversation(baseRequest)) {
				PlannerExecutionResult failure = new PlannerExecutionResult(
					baseRequest,
					null,
					LlmUsageSnapshot.unknown(),
					LlmFailureType.PROVIDER_ERROR,
					"Planner request could not be submitted after compaction"
				);
				clearState();
				return failure;
			}
			return null;
		}

		if (toolResultFuture != null) {
			if (!toolResultFuture.isDone()) {
				return null;
			}
			return continueAfterTool();
		}

		PlannerExecutionResult plannerResult = plannerExecutor.poll();
		if (plannerResult == null) {
			return null;
		}
		if (!plannerResult.succeeded()) {
			clearState();
			return plannerResult;
		}

		contextAggregator.recordUsage(plannerResult.usage());
		PlannerToolRequest toolRequest = plannerResult.response().toolRequest();
		if (toolRequest == null) {
			clearState();
			return plannerResult;
		}

		if (toolUsed) {
			Airicraft.LOGGER.warn(
				"Planner returned repeated tool request type={} prompt={}",
				toolRequest.type(),
				summarizeForLog(toolRequest.prompt())
			);
			clearState();
			return parseFailure("Planner requested describe_current_view more than once");
		}
		if (!hasToolCompatibleIntent(plannerResult.response())) {
			Airicraft.LOGGER.warn(
				"Planner returned invalid tool response intentType={} toolRequestType={} toolPrompt={} replyText={}",
				plannerResult.response().intent() == null ? null : plannerResult.response().intent().type(),
				toolRequest.type(),
				summarizeForLog(toolRequest.prompt()),
				summarizeForLog(plannerResult.response().replyText())
			);
			clearState();
			return parseFailure("Tool requests must set intent.type to none");
		}
		if (!"describe_current_view".equals(toolRequest.type()) || toolRequest.prompt() == null || toolRequest.prompt().isBlank()) {
			Airicraft.LOGGER.warn(
				"Planner returned invalid tool request type={} prompt={}",
				toolRequest.type(),
				summarizeForLog(toolRequest.prompt())
			);
			clearState();
			return parseFailure("Planner requested an invalid tool");
		}
		if (plannerResult.response().replyText() != null && !plannerResult.response().replyText().isBlank()) {
			Airicraft.LOGGER.info(
				"Planner returned tool request with stray replyText; ignoring text={} toolRequestType={}",
				summarizeForLog(plannerResult.response().replyText()),
				toolRequest.type()
			);
		}

		toolUsed = true;
		toolResultFuture = requestVisionTool(toolRequest.prompt());
		return null;
	}

	public void injectMockResponse(PlannerResponse response) {
		plannerExecutor.injectMockResponse(response);
	}

	public void injectTimeout() {
		plannerExecutor.injectTimeout();
	}

	public void recordAssistantTurn(DialogueTurn turn) {
		contextAggregator.recordAgentTurn(turn);
	}

	public void recordEvents(java.util.List<SemanticEvent> events, long anchorTimeMs) {
		contextAggregator.recordEvents(events, anchorTimeMs);
	}

	public boolean startDebugCompaction() {
		if (!isConfigured() || hasInFlight()) {
			return false;
		}
		lastCompactionResult = null;
		return compactionService.submit(contextAggregator.buildCompactionConversation());
	}

	public CompactionExecutionResult pollDebugCompaction() {
		if (!compactionService.hasInFlight()) {
			return lastCompactionResult;
		}
		CompactionExecutionResult compactionResult = compactionService.poll();
		if (compactionResult == null) {
			return null;
		}
		completeCompaction(compactionResult);
		return compactionResult;
	}

	public void reset() {
		clearState();
		plannerExecutor.reset();
		compactionService.reset();
		contextAggregator.clear();
		lastCompactionResult = null;
	}

	public void shutdown() {
		reset();
		plannerExecutor.shutdown();
		compactionService.shutdown();
	}

	private boolean submitPlannerConversation(PlannerRequest request) {
		LlmConversation conversation = request.toolResult() == null || request.toolResult().isBlank()
			? contextAggregator.buildPlannerConversation(request)
			: contextAggregator.buildPlannerFollowUpConversation(request.toolResult());
		return plannerExecutor.submit(request, conversation);
	}

	private PlannerExecutionResult continueAfterTool() {
		String toolResultText;
		try {
			toolResultText = toolResultFuture.join();
		}
		catch (CompletionException exception) {
			toolResultText = "VISION_UNAVAILABLE: vision_failed";
			Airicraft.LOGGER.warn("Planner tool future failed sender={}", baseRequest == null ? null : baseRequest.senderName(), exception);
		}
		finally {
			toolResultFuture = null;
		}

		PlannerRequest followUpRequest = new PlannerRequest(
			baseRequest.tick(),
			baseRequest.timestampMs(),
			baseRequest.sessionMode(),
			baseRequest.primaryInteractionPlayer(),
			baseRequest.activeGoal(),
			baseRequest.senderName(),
			baseRequest.message(),
			toolResultText
		);
		if (!submitPlannerConversation(followUpRequest)) {
			PlannerExecutionResult failure = new PlannerExecutionResult(
				followUpRequest,
				null,
				LlmUsageSnapshot.unknown(),
				LlmFailureType.PROVIDER_ERROR,
				"Planner follow-up request could not be submitted"
			);
			clearState();
			return failure;
		}
		return null;
	}

	private CompletableFuture<String> requestVisionTool(String prompt) {
		if (!visionTool.isConfigured()) {
			return CompletableFuture.completedFuture("VISION_UNAVAILABLE: vision_provider_unavailable");
		}

		return visionTool.requestDescription(prompt)
			.handle((description, throwable) -> {
				if (throwable == null) {
					return description.text();
				}
				String code = visionFailureCode(throwable);
				Airicraft.LOGGER.warn("Vision tool failed code={}", code, throwable);
				return "VISION_UNAVAILABLE: " + code;
			});
	}

	private static boolean hasToolCompatibleIntent(PlannerResponse response) {
		PlannerIntent intent = response.intent();
		String intentType = intent == null || intent.type() == null ? "none" : intent.type();
		return "none".equals(intentType);
	}

	private PlannerExecutionResult parseFailure(String message) {
		return new PlannerExecutionResult(baseRequest, null, LlmUsageSnapshot.unknown(), LlmFailureType.PARSE_ERROR, message);
	}

	private void completeCompaction(CompactionExecutionResult compactionResult) {
		lastCompactionResult = compactionResult;
		if (compactionResult.succeeded()) {
			contextAggregator.recordObservedUsage(compactionResult.usage());
			contextAggregator.applyCheckpoint(compactionResult.checkpoint());
			return;
		}
		Airicraft.LOGGER.warn("Planner compaction failed message={}", summarizeForLog(compactionResult.failureMessage()));
		contextAggregator.onCompactionFailure();
	}

	private void clearState() {
		baseRequest = null;
		toolUsed = false;
		toolResultFuture = null;
	}

	private static String visionFailureCode(Throwable throwable) {
		Throwable cause = throwable instanceof CompletionException completionException && completionException.getCause() != null
			? completionException.getCause()
			: throwable;
		if (cause instanceof BridgeUnavailableException bridgeUnavailableException) {
			return bridgeUnavailableException.code();
		}
		if (cause instanceof LlmBackendException backendException) {
			return switch (backendException.failureType()) {
				case PROVIDER_UNAVAILABLE -> "vision_provider_unavailable";
				case TIMEOUT -> "vision_timeout";
				case PROVIDER_ERROR, PARSE_ERROR -> "vision_failed";
			};
		}
		return "vision_failed";
	}

	private static String summarizeForLog(String text) {
		return OpenAiCompatibleChatClient.summarizeForLog(text);
	}
}
