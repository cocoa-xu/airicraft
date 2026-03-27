package ai.moeru.airicraft.agent.dialogue;

public record DialogueResponse(
	String text,
	DialogueIntent intent,
	long tick
) {
}
