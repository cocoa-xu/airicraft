package ai.moeru.airicraft.agent.llm;

public record CompactionExecutionResult(
	CompactionCheckpoint checkpoint,
	LlmUsageSnapshot usage,
	LlmFailureType failureType,
	String failureMessage
) {
	public boolean succeeded() {
		return failureType == null;
	}
}
