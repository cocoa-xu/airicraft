package ai.moeru.airicraft.agent.dialogue;

import ai.moeru.airicraft.agent.events.SemanticEventBuffer;
import ai.moeru.airicraft.agent.goals.GoalSnapshot;
import ai.moeru.airicraft.agent.llm.CurrentViewVisionTool;
import ai.moeru.airicraft.agent.llm.LlmFailureType;
import ai.moeru.airicraft.agent.llm.OpenAiCompatibleLlmBackend;
import ai.moeru.airicraft.agent.llm.PlannerExecutionResult;
import ai.moeru.airicraft.agent.llm.PlannerExecutor;
import ai.moeru.airicraft.agent.llm.PlannerOrchestrator;
import ai.moeru.airicraft.agent.llm.PlannerRequest;
import ai.moeru.airicraft.agent.llm.PlannerResponse;
import ai.moeru.airicraft.agent.session.SessionSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class DialogueRuntime {
	private static final int DEGRADED_FAILURE_THRESHOLD = 3;
	private static final String RESET_COMMAND = "@agent reset";
	private static final String DEGRADED_MESSAGE = "I'm having trouble understanding right now. Send '@agent reset' to recover my planner.";
	private static final String RESET_MESSAGE = "Planner state reset.";
	private static final String PARSE_ERROR_MESSAGE = "I got confused for a moment.";

	private final PlannerOrchestrator plannerOrchestrator;
	private final int maxRecentTurns;
	private final List<DialogueTurn> recentTurns = new ArrayList<>();

	private DialogueResponse lastResponse;
	private boolean pendingReply;
	private boolean degraded;
	private int consecutiveFailureCount;
	private int queuedTimeoutInjections;
	private LlmFailureType lastFailureType;
	private long lastFailureTick = -1L;

	public DialogueRuntime() {
		this(
			new PlannerOrchestrator(
				new PlannerExecutor(new OpenAiCompatibleLlmBackend(ai.moeru.airicraft.agent.AgentConfig.LlmConfig.defaults())),
				CurrentViewVisionTool.disabled()
			),
			8
		);
	}

	public DialogueRuntime(PlannerOrchestrator plannerOrchestrator, int maxRecentTurns) {
		this.plannerOrchestrator = plannerOrchestrator;
		this.maxRecentTurns = Math.max(1, maxRecentTurns);
	}

	public DialogueRuntime(PlannerExecutor plannerExecutor, int maxRecentTurns) {
		this(new PlannerOrchestrator(plannerExecutor, CurrentViewVisionTool.disabled()), maxRecentTurns);
	}

	public void recordResponse(DialogueResponse response) {
		lastResponse = response;
		pendingReply = response != null && response.text() != null && !response.text().isBlank();
	}

	public Optional<DialogueResponse> lastResponse() {
		return Optional.ofNullable(lastResponse);
	}

	public boolean hasPendingReply() {
		return pendingReply;
	}

	public void markReplyObserved() {
		pendingReply = false;
	}

	public boolean isDegraded() {
		return degraded;
	}

	public boolean llmAvailable() {
		return plannerOrchestrator.isConfigured();
	}

	public long lastFailureTick() {
		return lastFailureTick;
	}

	public int consecutiveFailureCount() {
		return consecutiveFailureCount;
	}

	public LlmFailureType lastFailureType() {
		return lastFailureType;
	}

	public DialogueSnapshot snapshot() {
		return new DialogueSnapshot(
			List.copyOf(recentTurns),
			lastResponse,
			degraded,
			consecutiveFailureCount,
			lastFailureType,
			lastFailureTick
		);
	}

	public void injectMockResponse(PlannerResponse response) {
		plannerOrchestrator.injectMockResponse(response);
	}

	public void injectTimeout() {
		queuedTimeoutInjections++;
	}

	public boolean handleResetCommand(String senderName, String plainTextMessage, long tick, SemanticEventBuffer eventBuffer) {
		if (!isResetCommand(plainTextMessage)) {
			return false;
		}

		appendTurn(new DialogueTurn(senderName, plainTextMessage, tick));
		eventBuffer.append(tick, "planner.reset_requested", Map.of(
			"player", senderName
		));
		resetLlmState(tick, eventBuffer);
		recordResponse(new DialogueResponse(
			RESET_MESSAGE,
			new DialogueIntent(DialogueIntentType.ACKNOWLEDGE_FAILURE, null, senderName),
			tick
		));
		appendTurn(new DialogueTurn("agent", RESET_MESSAGE, tick));
		return true;
	}

	public void onPlayerChat(
		String senderName,
		String plainTextMessage,
		long tick,
		SessionSnapshot sessionSnapshot,
		String primaryInteractionPlayer,
		Optional<GoalSnapshot> activeGoal
	) {
		appendTurn(new DialogueTurn(senderName, plainTextMessage, tick));
		if (degraded || plannerOrchestrator.hasInFlight()) {
			return;
		}

		plannerOrchestrator.submit(new PlannerRequest(
			tick,
			sessionSnapshot.mode(),
			primaryInteractionPlayer,
			activeGoal.orElse(null),
			List.copyOf(recentTurns),
			senderName,
			plainTextMessage,
			null
		));
	}

	public DialogueResponse poll(long tick, SemanticEventBuffer eventBuffer) {
		if (queuedTimeoutInjections > 0 && !plannerOrchestrator.hasInFlight()) {
			queuedTimeoutInjections--;
			onFailure(LlmFailureType.TIMEOUT, "Injected LLM timeout", tick, eventBuffer);
			return null;
		}

		PlannerExecutionResult result = plannerOrchestrator.poll();
		if (result == null) {
			return null;
		}

		if (!result.succeeded()) {
			onFailure(result.failureType(), result.failureMessage(), tick, eventBuffer);
			return null;
		}

		consecutiveFailureCount = 0;
		PlannerResponse plannerResponse = result.response();
		DialogueIntentType mappedIntentType = DialogueIntentType.fromWire(plannerResponse.intent().type()).orElse(null);
		if (mappedIntentType == null) {
			eventBuffer.append(tick, "planner.unknown_intent", Map.of(
				"type", plannerResponse.intent().type()
			));
			mappedIntentType = DialogueIntentType.NONE;
		}

		DialogueResponse response = new DialogueResponse(
			plannerResponse.replyText() == null ? "" : plannerResponse.replyText(),
			new DialogueIntent(mappedIntentType, plannerResponse.intent().goalType(), plannerResponse.intent().targetPlayer()),
			tick
		);
		recordResponse(response);
		if (response.text() != null && !response.text().isBlank()) {
			appendTurn(new DialogueTurn("agent", response.text(), tick));
		}
		return response;
	}

	public void resetLlmState(long tick, SemanticEventBuffer eventBuffer) {
		boolean wasDegraded = degraded;
		plannerOrchestrator.reset();
		degraded = false;
		consecutiveFailureCount = 0;
		queuedTimeoutInjections = 0;
		lastFailureType = null;
		lastFailureTick = -1L;
		if (wasDegraded) {
			eventBuffer.append(tick, "planner.degraded_cleared", Map.of());
		}
	}

	public void clear() {
		lastResponse = null;
		pendingReply = false;
		degraded = false;
		consecutiveFailureCount = 0;
		queuedTimeoutInjections = 0;
		lastFailureType = null;
		lastFailureTick = -1L;
		recentTurns.clear();
		plannerOrchestrator.reset();
	}

	public void shutdown() {
		clear();
		plannerOrchestrator.shutdown();
	}

	public static boolean isResetCommand(String plainTextMessage) {
		if (plainTextMessage == null) {
			return false;
		}
		return plainTextMessage.stripLeading().equalsIgnoreCase(RESET_COMMAND);
	}

	private void onFailure(LlmFailureType failureType, String failureMessage, long tick, SemanticEventBuffer eventBuffer) {
		lastFailureType = failureType;
		lastFailureTick = tick;
		consecutiveFailureCount++;

		String eventType = switch (failureType) {
			case TIMEOUT -> "planner.timeout";
			case PARSE_ERROR -> "planner.parse_error";
			case PROVIDER_ERROR, PROVIDER_UNAVAILABLE -> "planner.provider_error";
		};
		eventBuffer.append(tick, eventType, Map.of(
			"failureType", failureType.name(),
			"message", failureMessage == null ? "" : failureMessage
		));

		if (failureType == LlmFailureType.PARSE_ERROR) {
			recordResponse(new DialogueResponse(
				PARSE_ERROR_MESSAGE,
				new DialogueIntent(DialogueIntentType.ACKNOWLEDGE_FAILURE, null, null),
				tick
			));
			appendTurn(new DialogueTurn("agent", PARSE_ERROR_MESSAGE, tick));
		}

		if (consecutiveFailureCount >= DEGRADED_FAILURE_THRESHOLD && !degraded) {
			degraded = true;
			eventBuffer.append(tick, "planner.degraded_entered", Map.of(
				"failureType", failureType.name(),
				"consecutiveFailureCount", consecutiveFailureCount
			));
			recordResponse(new DialogueResponse(
				DEGRADED_MESSAGE,
				new DialogueIntent(DialogueIntentType.ACKNOWLEDGE_FAILURE, null, null),
				tick
			));
			appendTurn(new DialogueTurn("agent", DEGRADED_MESSAGE, tick));
		}
	}

	private void appendTurn(DialogueTurn turn) {
		recentTurns.add(turn);
		while (recentTurns.size() > maxRecentTurns) {
			recentTurns.remove(0);
		}
	}
}
