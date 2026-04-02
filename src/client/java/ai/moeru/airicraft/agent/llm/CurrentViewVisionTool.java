package ai.moeru.airicraft.agent.llm;

import ai.moeru.airicraft.FirstPersonScreenshotService;

import java.util.concurrent.CompletableFuture;

public interface CurrentViewVisionTool {
	boolean isConfigured();

	CompletableFuture<FirstPersonScreenshotService.CapturedScreenshot> requestCapture();

	CompletableFuture<VisionDescription> requestDescription(FirstPersonScreenshotService.CapturedScreenshot screenshot, String prompt);

	default CompletableFuture<VisionDescription> requestDescription(String prompt) {
		return requestCapture().thenCompose(screenshot -> requestDescription(screenshot, prompt));
	}

	static CurrentViewVisionTool disabled() {
		return new CurrentViewVisionTool() {
			@Override
			public boolean isConfigured() {
				return false;
			}

			@Override
			public CompletableFuture<FirstPersonScreenshotService.CapturedScreenshot> requestCapture() {
				return CompletableFuture.failedFuture(
					new LlmBackendException(LlmFailureType.PROVIDER_UNAVAILABLE, "Vision provider is not configured")
				);
			}

			@Override
			public CompletableFuture<VisionDescription> requestDescription(FirstPersonScreenshotService.CapturedScreenshot screenshot, String prompt) {
				return CompletableFuture.failedFuture(
					new LlmBackendException(LlmFailureType.PROVIDER_UNAVAILABLE, "Vision provider is not configured")
				);
			}
		};
	}
}
