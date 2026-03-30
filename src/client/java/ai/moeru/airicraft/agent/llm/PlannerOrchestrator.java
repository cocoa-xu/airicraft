package ai.moeru.airicraft.agent.llm;

import ai.moeru.airicraft.Airicraft;
import ai.moeru.airicraft.BridgeUnavailableException;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public final class PlannerOrchestrator {
	private final PlannerExecutor plannerExecutor;
	private final CurrentViewVisionTool visionTool;

	private PlannerRequest baseRequest;
	private boolean toolUsed;
	private CompletableFuture<String> toolResultFuture;

	public PlannerOrchestrator(PlannerExecutor plannerExecutor, CurrentViewVisionTool visionTool) {
		this.plannerExecutor = Objects.requireNonNull(plannerExecutor, "plannerExecutor");
		this.visionTool = Objects.requireNonNull(visionTool, "visionTool");
	}

	public boolean isConfigured() {
		return plannerExecutor.isConfigured();
	}

	public boolean hasInFlight() {
		return plannerExecutor.hasInFlight() || toolResultFuture != null;
	}

	public boolean submit(PlannerRequest request) {
		Objects.requireNonNull(request, "request");
		if (hasInFlight()) {
			return false;
		}

		baseRequest = request;
		toolUsed = false;
		return plannerExecutor.submit(request);
	}

	public PlannerExecutionResult poll() {
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
		Airicraft.LOGGER.info(
			"Planner requested tool type={} sender={} prompt={}",
			toolRequest.type(),
			baseRequest == null ? null : baseRequest.senderName(),
			summarizeForLog(toolRequest.prompt())
		);
		toolResultFuture = requestVisionTool(toolRequest.prompt());
		return null;
	}

	public void injectMockResponse(PlannerResponse response) {
		plannerExecutor.injectMockResponse(response);
	}

	public void injectTimeout() {
		plannerExecutor.injectTimeout();
	}

	public void reset() {
		clearState();
		plannerExecutor.reset();
	}

	public void shutdown() {
		reset();
		plannerExecutor.shutdown();
	}

	private PlannerExecutionResult continueAfterTool() {
		String toolResultText;
		try {
			toolResultText = toolResultFuture.join();
			Airicraft.LOGGER.info("Planner tool result sender={} value={}", baseRequest.senderName(), summarizeForLog(toolResultText));
		}
		catch (CompletionException exception) {
			toolResultText = "VISION_UNAVAILABLE: vision_failed";
			Airicraft.LOGGER.warn("Planner tool future failed sender={}", baseRequest.senderName(), exception);
		}
		finally {
			toolResultFuture = null;
		}

		PlannerRequest followUpRequest = new PlannerRequest(
			baseRequest.tick(),
			baseRequest.sessionMode(),
			baseRequest.primaryInteractionPlayer(),
			baseRequest.activeGoal(),
			baseRequest.recentTurns(),
			baseRequest.senderName(),
			baseRequest.message(),
			toolResultText
		);
		if (!plannerExecutor.submit(followUpRequest)) {
			Airicraft.LOGGER.warn("Planner follow-up submission failed sender={}", followUpRequest.senderName());
			clearState();
			return new PlannerExecutionResult(
				followUpRequest,
				null,
				LlmFailureType.PROVIDER_ERROR,
				"Planner follow-up request could not be submitted"
			);
		}
		return null;
	}

	private CompletableFuture<String> requestVisionTool(String prompt) {
		if (!visionTool.isConfigured()) {
			Airicraft.LOGGER.info("Vision tool unavailable: provider not configured");
			return CompletableFuture.completedFuture("VISION_UNAVAILABLE: vision_provider_unavailable");
		}

		return visionTool.requestDescription(prompt)
			.handle((description, throwable) -> {
				if (throwable == null) {
					Airicraft.LOGGER.info("Vision tool succeeded text={}", summarizeForLog(description.text()));
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
		return new PlannerExecutionResult(baseRequest, null, LlmFailureType.PARSE_ERROR, message);
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
		if (text == null) {
			return "";
		}
		String normalized = text
			.replace("\\", "\\\\")
			.replace("\r", "\\r")
			.replace("\n", "\\n");
		if (normalized.length() > 600) {
			return normalized.substring(0, 600) + "...";
		}
		return normalized;
	}
}
