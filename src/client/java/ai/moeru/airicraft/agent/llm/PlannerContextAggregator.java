package ai.moeru.airicraft.agent.llm;

import ai.moeru.airicraft.agent.dialogue.DialogueTurn;
import ai.moeru.airicraft.agent.events.SemanticEvent;

import java.time.Clock;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class PlannerContextAggregator {
	private final Clock clock;
	private final ZoneId zoneId;
	private final int compactionTriggerTokens;

	private PlannerContextState state = PlannerContextState.initial();
	private LlmConversation frozenPlannerConversation;

	public PlannerContextAggregator(Clock clock, int compactionTriggerTokens) {
		this.clock = Objects.requireNonNull(clock, "clock");
		this.zoneId = clock.getZone();
		this.compactionTriggerTokens = compactionTriggerTokens;
	}

	public boolean compactionPending() {
		return state.compactionPending();
	}

	public LlmUsageSnapshot lastObservedUsage() {
		return state.lastObservedUsage();
	}

	public PlannerContextDebugSnapshot debugSnapshot() {
		return new PlannerContextDebugSnapshot(
			compactionTriggerTokens,
			state.compactionPending(),
			state.rawArchiveTape().size(),
			state.canonicalTape().size(),
			state.pendingEntries().size(),
			frozenPlannerConversation == null ? 0 : frozenPlannerConversation.messages().size(),
			state.lastObservedEventSeqNo(),
			state.lastTimeBeaconAtMs(),
			state.lastObservedUsage(),
			state.lastAmbientContext(),
			state.activeCheckpoint()
		);
	}

	public void recordEvents(List<SemanticEvent> events, long anchorTimeMs) {
		if (events == null || events.isEmpty()) {
			return;
		}

		long previousSeqNo = state.lastObservedEventSeqNo();
		long latestSeqNo = previousSeqNo;
		ArrayList<PlannerContextEntry> notices = new ArrayList<>();
		boolean sawGap = false;
		for (SemanticEvent event : events) {
			if (event.seqNo() <= previousSeqNo) {
				continue;
			}
			if (!sawGap && event.seqNo() > previousSeqNo + 1L) {
				notices.add(new PlannerContextEntry(
					PlannerContextEntryType.NOTICE,
					null,
					"Some earlier context events were dropped before they could be summarized.",
					event.tick(),
					event.timestampMs()
				));
				sawGap = true;
			}
			String rendered = SemanticEventNoticeFormatter.format(event, anchorTimeMs);
			if (rendered != null && !rendered.isBlank()) {
				notices.add(new PlannerContextEntry(
					PlannerContextEntryType.NOTICE,
					null,
					rendered,
					event.tick(),
					event.timestampMs()
				));
			}
			latestSeqNo = Math.max(latestSeqNo, event.seqNo());
		}
		state = PlannerContextReducer.recordEntries(state, notices);
		state = PlannerContextReducer.updateObservedEventSeqNo(state, latestSeqNo);
	}

	public LlmConversation buildPlannerConversation(PlannerRequest request) {
		Objects.requireNonNull(request, "request");
		long nowMs = request.timestampMs();
		if (PlannerContextPolicy.shouldInjectTimeBeacon(state.lastTimeBeaconAtMs(), nowMs)) {
			state = PlannerContextReducer.recordEntry(state, new PlannerContextEntry(
				PlannerContextEntryType.NOTICE,
				null,
				PlannerContextPolicy.timeBeaconText(nowMs, zoneId),
				-1L,
				nowMs
			));
			state = PlannerContextReducer.updateTimeBeacon(state, nowMs);
		}

		PlannerAmbientContext ambientContext = PlannerAmbientContext.fromRequest(request);
		state = PlannerContextReducer.recordEntries(
			state,
			PlannerAmbientContextRenderer.renderChanges(state.lastAmbientContext(), ambientContext, request.tick(), nowMs)
		);
		state = PlannerContextReducer.updateAmbientContext(state, ambientContext);

		state = PlannerContextReducer.recordEntry(state, new PlannerContextEntry(
			PlannerContextEntryType.USER_TURN,
			request.senderName(),
			request.message(),
			request.tick(),
			request.timestampMs()
		));
		state = PlannerContextReducer.commitPending(state, nowMs);
		frozenPlannerConversation = composeConversation(state.canonicalTape(), null);
		return frozenPlannerConversation;
	}

	public LlmConversation buildPlannerFollowUpConversation(String toolResult) {
		if (frozenPlannerConversation == null) {
			throw new IllegalStateException("No frozen planner conversation");
		}
		return frozenPlannerConversation.withAppended(
			LlmChatMessage.user("Tool result: " + (toolResult == null || toolResult.isBlank() ? "none" : toolResult), LlmMessageKind.TOOL_RESULT)
		);
	}

	public LlmConversation buildCompactionConversation() {
		long nowMs = clock.millis();
		state = PlannerContextReducer.commitPending(state, nowMs);
		return composeConversation(
			state.canonicalTape(),
			LlmChatMessage.user(PlannerPromptPolicy.compactionInstruction(), LlmMessageKind.TASK)
		);
	}

	public void recordAgentTurn(DialogueTurn turn) {
		Objects.requireNonNull(turn, "turn");
		state = PlannerContextReducer.recordEntry(state, new PlannerContextEntry(
			PlannerContextEntryType.ASSISTANT_TURN,
			turn.speaker(),
			turn.text(),
			turn.tick(),
			turn.timestampMs()
		));
	}

	public void recordUsage(LlmUsageSnapshot usage) {
		state = PlannerContextReducer.updateUsage(state, usage, compactionTriggerTokens);
	}

	public void recordObservedUsage(LlmUsageSnapshot usage) {
		state = PlannerContextReducer.updateObservedUsage(state, usage, state.compactionPending());
	}

	public void applyCheckpoint(CompactionCheckpoint checkpoint) {
		state = PlannerContextReducer.clearCompactionPending(state, checkpoint, clock.millis());
		frozenPlannerConversation = null;
	}

	public void onCompactionFailure() {
		frozenPlannerConversation = null;
	}

	public void clear() {
		state = PlannerContextState.initial();
		frozenPlannerConversation = null;
	}

	private LlmConversation composeConversation(List<LlmChatMessage> canonicalTape, LlmChatMessage terminalMessage) {
		ArrayList<LlmChatMessage> messages = new ArrayList<>();
		messages.add(LlmChatMessage.system(PlannerPromptPolicy.SYSTEM_PROMPT));
		if (state.activeCheckpoint() != null) {
			messages.add(LlmChatMessage.user(state.activeCheckpoint().renderMessage(), LlmMessageKind.CHECKPOINT));
		}
		messages.addAll(canonicalTape);
		if (terminalMessage != null) {
			messages.add(terminalMessage);
		}
		return LlmConversation.of(messages);
	}
}
