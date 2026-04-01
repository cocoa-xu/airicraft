package ai.moeru.airicraft.agent.llm;

public enum LlmMessageKind {
	SYSTEM,
	CHECKPOINT,
	NOTICE,
	USER_TURN,
	ASSISTANT_TURN,
	TOOL_RESULT,
	TASK
}
