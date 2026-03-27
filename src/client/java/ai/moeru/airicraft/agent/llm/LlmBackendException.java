package ai.moeru.airicraft.agent.llm;

public final class LlmBackendException extends Exception {
	private final LlmFailureType failureType;

	public LlmBackendException(LlmFailureType failureType, String message) {
		super(message);
		this.failureType = failureType;
	}

	public LlmBackendException(LlmFailureType failureType, String message, Throwable cause) {
		super(message, cause);
		this.failureType = failureType;
	}

	public LlmFailureType failureType() {
		return failureType;
	}
}
