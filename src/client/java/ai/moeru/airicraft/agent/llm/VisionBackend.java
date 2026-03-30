package ai.moeru.airicraft.agent.llm;

public interface VisionBackend {
	VisionDescription describe(VisionRequest request) throws LlmBackendException;

	boolean isConfigured();
}
