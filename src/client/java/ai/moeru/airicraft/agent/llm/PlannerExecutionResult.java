package ai.moeru.airicraft.agent.llm;

public record PlannerExecutionResult(
	PlannerRequest request,
	PlannerResponse response,
	LlmFailureType failureType,
	String failureMessage
) {
	public boolean succeeded() {
		return failureType == null;
	}
}
