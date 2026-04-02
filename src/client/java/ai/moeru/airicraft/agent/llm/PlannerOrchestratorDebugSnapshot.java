package ai.moeru.airicraft.agent.llm;

public record PlannerOrchestratorDebugSnapshot(
	boolean configured,
	String plannerVisionMode,
	boolean inFlight,
	boolean plannerInFlight,
	boolean compactionInFlight,
	boolean captureInFlight,
	boolean toolInFlight,
	boolean toolUsed,
	PlannerRequest baseRequest,
	CompactionExecutionResult lastCompactionResult,
	PlannerContextDebugSnapshot context
) {
}
