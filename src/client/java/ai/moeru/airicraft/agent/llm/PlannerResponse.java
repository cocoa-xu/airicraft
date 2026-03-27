package ai.moeru.airicraft.agent.llm;

public record PlannerResponse(
	String replyText,
	PlannerIntent intent
) {
}
