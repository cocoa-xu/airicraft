package ai.moeru.airicraft.agent.dialogue;

public record DialogueTurn(
	String speaker,
	String text,
	long tick,
	long timestampMs
) {
}
