package ai.moeru.airicraft.agent.llm;

import ai.moeru.airicraft.BridgeUnavailableException;
import ai.moeru.airicraft.FirstPersonScreenshotService;
import net.minecraft.client.MinecraftClient;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

public final class CurrentViewVisionService implements CurrentViewVisionTool {
	public static final String DEFAULT_DESCRIBE_PROMPT =
		"Describe the current Minecraft first-person view in one short paragraph. " +
			"Mention terrain, nearby landmarks, hazards, structures, and whether the scene feels indoors or outdoors.";

	private final FirstPersonScreenshotService screenshotService;
	private final VisionBackend visionBackend;
	private final Supplier<MinecraftClient> clientSupplier;
	private final ExecutorService executorService;

	public CurrentViewVisionService(
		FirstPersonScreenshotService screenshotService,
		VisionBackend visionBackend,
		Supplier<MinecraftClient> clientSupplier
	) {
		this.screenshotService = Objects.requireNonNull(screenshotService, "screenshotService");
		this.visionBackend = Objects.requireNonNull(visionBackend, "visionBackend");
		this.clientSupplier = Objects.requireNonNull(clientSupplier, "clientSupplier");
		this.executorService = Executors.newSingleThreadExecutor(runnable -> {
			Thread thread = new Thread(runnable, "airicraft-vision");
			thread.setDaemon(true);
			return thread;
		});
	}

	@Override
	public boolean isConfigured() {
		return visionBackend.isConfigured();
	}

	@Override
	public CompletableFuture<VisionDescription> requestDescription(String prompt) {
		if (!isConfigured()) {
			return CompletableFuture.failedFuture(
				new LlmBackendException(LlmFailureType.PROVIDER_UNAVAILABLE, "Vision provider is not configured")
			);
		}

		MinecraftClient client = clientSupplier.get();
		if (client == null || client.world == null || client.player == null) {
			return CompletableFuture.failedFuture(
				new BridgeUnavailableException("world_not_loaded", "No world is currently loaded")
			);
		}

		try {
			CompletableFuture<FirstPersonScreenshotService.CapturedScreenshot> captureFuture = screenshotService.requestCapture(client);
			return captureFuture.thenCompose(capture ->
				CompletableFuture.supplyAsync(() -> {
					try {
						return describe(capture, prompt);
					}
					catch (LlmBackendException exception) {
						throw new CompletionException(exception);
					}
				}, executorService)
			);
		}
		catch (RuntimeException exception) {
			return CompletableFuture.failedFuture(exception);
		}
	}

	public VisionDescription describe(FirstPersonScreenshotService.CapturedScreenshot screenshot, String prompt) throws LlmBackendException {
		Objects.requireNonNull(screenshot, "screenshot");
		return visionBackend.describe(new VisionRequest(
			normalizePrompt(prompt),
			"image/png",
			screenshot.imageBytes(),
			screenshot.capturedAtMs()
		));
	}

	public void shutdown() {
		executorService.shutdownNow();
	}

	private static String normalizePrompt(String prompt) {
		if (prompt == null || prompt.isBlank()) {
			return DEFAULT_DESCRIBE_PROMPT;
		}
		return prompt;
	}
}
