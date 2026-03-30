package ai.moeru.airicraft.agent.llm;

import java.util.concurrent.CompletableFuture;

public interface CurrentViewVisionTool {
	boolean isConfigured();

	CompletableFuture<VisionDescription> requestDescription(String prompt);

	static CurrentViewVisionTool disabled() {
		return new CurrentViewVisionTool() {
			@Override
			public boolean isConfigured() {
				return false;
			}

			@Override
			public CompletableFuture<VisionDescription> requestDescription(String prompt) {
				return CompletableFuture.failedFuture(
					new LlmBackendException(LlmFailureType.PROVIDER_UNAVAILABLE, "Vision provider is not configured")
				);
			}
		};
	}
}
