package ai.moeru.airicraft.agent.llm;

public record PlannerToolRequest(
	String type,
	String prompt
) {
}
