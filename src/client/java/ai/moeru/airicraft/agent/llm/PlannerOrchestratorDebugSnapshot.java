package ai.moeru.airicraft.agent.llm;

public record PlannerOrchestratorDebugSnapshot(
	boolean configured,
	boolean inFlight,
	boolean plannerInFlight,
	boolean compactionInFlight,
	boolean toolInFlight,
	boolean toolUsed,
	PlannerRequest baseRequest,
	CompactionExecutionResult lastCompactionResult,
	PlannerContextDebugSnapshot context
) {
}
