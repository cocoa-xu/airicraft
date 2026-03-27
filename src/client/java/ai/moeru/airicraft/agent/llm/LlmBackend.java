package ai.moeru.airicraft.agent.llm;

public interface LlmBackend {
	PlannerResponse generate(PlannerRequest request) throws LlmBackendException;

	void injectMockResponse(PlannerResponse response);

	void injectTimeout();

	boolean isConfigured();
}
