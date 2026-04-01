package ai.moeru.airicraft.agent.llm;

import ai.moeru.airicraft.agent.session.SessionMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class PlannerAmbientContextRenderer {
	private PlannerAmbientContextRenderer() {
	}

	public static List<PlannerContextEntry> renderChanges(
		PlannerAmbientContext previous,
		PlannerAmbientContext current,
		long tick,
		long timestampMs
	) {
		ArrayList<PlannerContextEntry> entries = new ArrayList<>();
		if (previous == null || previous.sessionMode() != current.sessionMode()) {
			entries.add(notice(describeSessionMode(current.sessionMode()), tick, timestampMs));
		}
		if (previous == null || !Objects.equals(previous.primaryInteractionPlayer(), current.primaryInteractionPlayer())) {
			entries.add(notice(describePrimaryInteraction(current.primaryInteractionPlayer()), tick, timestampMs));
		}
		if (previous == null || !Objects.equals(previous.activeGoalDescription(), current.activeGoalDescription())) {
			entries.add(notice(describeActiveGoal(current.activeGoalDescription()), tick, timestampMs));
		}
		return List.copyOf(entries);
	}

	private static PlannerContextEntry notice(String text, long tick, long timestampMs) {
		return new PlannerContextEntry(PlannerContextEntryType.NOTICE, null, text, tick, timestampMs);
	}

	private static String describeSessionMode(SessionMode sessionMode) {
		if (sessionMode == null) {
			return "Session mode is unknown right now.";
		}
		return switch (sessionMode) {
			case OUT_OF_WORLD -> "Session mode is currently out of world.";
			case SINGLEPLAYER_LOCAL -> "Session mode is currently singleplayer local.";
			case SINGLEPLAYER_LAN_HOST -> "Session mode is currently singleplayer LAN host.";
			case REMOTE_MULTIPLAYER -> "Session mode is currently remote multiplayer.";
		};
	}

	private static String describePrimaryInteraction(String primaryInteractionPlayer) {
		return primaryInteractionPlayer == null
			? "There is no primary interaction player right now."
			: "Primary interaction player is " + primaryInteractionPlayer + ".";
	}

	private static String describeActiveGoal(String activeGoalDescription) {
		return activeGoalDescription == null
			? "There is no active goal right now."
			: "Active goal: " + activeGoalDescription;
	}
}
