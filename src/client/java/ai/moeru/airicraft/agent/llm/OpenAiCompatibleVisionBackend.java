package ai.moeru.airicraft.agent.llm;

import ai.moeru.airicraft.Airicraft;
import ai.moeru.airicraft.agent.AgentConfig;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class OpenAiCompatibleVisionBackend implements VisionBackend {
	private static final Gson GSON = new Gson();
	private static final String SYSTEM_PROMPT = """
		You describe a Minecraft first-person screenshot for another planner.
		Return a single short paragraph in plain text.
		Mention terrain, nearby landmarks, hazards, structures, and whether the view feels indoors or outdoors when visible.
		Do not use markdown, bullet lists, JSON, or multiple paragraphs.
		""";

	private final AgentConfig.LlmConfig config;
	private final HttpClient httpClient = HttpClient.newHttpClient();

	public OpenAiCompatibleVisionBackend(AgentConfig.LlmConfig config) {
		this.config = Objects.requireNonNull(config, "config");
	}

	@Override
	public VisionDescription describe(VisionRequest request) throws LlmBackendException {
		Objects.requireNonNull(request, "request");
		if (!isConfigured()) {
			throw new LlmBackendException(LlmFailureType.PROVIDER_UNAVAILABLE, "Vision provider is not configured");
		}

		String requestBody = GSON.toJson(buildRequestPayload(request));
		Airicraft.LOGGER.info(
			"Vision request model={} capturedAtMs={} prompt={}",
			config.visionModel(),
			request.capturedAtMs(),
			summarizeForLog(request.prompt())
		);
		HttpRequest httpRequest = HttpRequest.newBuilder()
			.uri(buildUri())
			.timeout(Duration.ofMillis(config.visionRequestTimeoutMillis()))
			.header("Authorization", "Bearer " + config.visionApiKey())
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
			.build();

		try {
			HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			Airicraft.LOGGER.info(
				"Vision response model={} status={} body={}",
				config.visionModel(),
				response.statusCode(),
				summarizeForLog(response.body())
			);
			if (response.statusCode() >= 400) {
				throw new LlmBackendException(LlmFailureType.PROVIDER_ERROR, "Vision provider returned HTTP " + response.statusCode());
			}

			return parseVisionResponse(response.body(), request.capturedAtMs());
		}
		catch (java.net.http.HttpTimeoutException exception) {
			throw new LlmBackendException(LlmFailureType.TIMEOUT, "Vision request timed out", exception);
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new LlmBackendException(LlmFailureType.TIMEOUT, "Vision request interrupted", exception);
		}
		catch (IOException exception) {
			throw new LlmBackendException(LlmFailureType.PROVIDER_ERROR, "Vision request failed", exception);
		}
	}

	@Override
	public boolean isConfigured() {
		return config.visionConfigured();
	}

	private URI buildUri() throws LlmBackendException {
		try {
			String baseUrl = config.visionProviderBaseUrl().endsWith("/")
				? config.visionProviderBaseUrl().substring(0, config.visionProviderBaseUrl().length() - 1)
				: config.visionProviderBaseUrl();
			return URI.create(baseUrl + "/chat/completions");
		}
		catch (IllegalArgumentException exception) {
			throw new LlmBackendException(LlmFailureType.PROVIDER_UNAVAILABLE, "Invalid vision provider URL", exception);
		}
	}

	private Map<String, Object> buildRequestPayload(VisionRequest request) {
		String imageUrl = "data:%s;base64,%s".formatted(
			request.mimeType(),
			Base64.getEncoder().encodeToString(request.imageBytes())
		);
		return Map.of(
			"model", config.visionModel(),
			"messages", List.of(
				Map.of("role", "system", "content", SYSTEM_PROMPT),
				Map.of(
					"role", "user",
					"content", List.of(
						Map.of("type", "text", "text", request.prompt()),
						Map.of(
							"type", "image_url",
							"image_url", Map.of(
								"url", imageUrl,
								"detail", config.visionImageDetail()
							)
						)
					)
				)
			)
		);
	}

	private VisionDescription parseVisionResponse(String responseBody, long capturedAtMs) throws LlmBackendException {
		try {
			JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();
			JsonArray choices = root.getAsJsonArray("choices");
			if (choices == null || choices.isEmpty()) {
				throw new JsonParseException("Missing choices");
			}

			JsonObject message = choices.get(0).getAsJsonObject().getAsJsonObject("message");
			if (message == null) {
				throw new JsonParseException("Missing message");
			}

			String content = OpenAiCompatibleMessageContent.extract(message.get("content")).trim();
			if (content.isBlank()) {
				throw new JsonParseException("Missing content");
			}
			Airicraft.LOGGER.info("Vision parsed response model={} text={}", config.visionModel(), summarizeForLog(content));
			return new VisionDescription(content, config.visionModel(), capturedAtMs);
		}
		catch (IllegalStateException | JsonParseException exception) {
			Airicraft.LOGGER.warn("Failed to parse vision response body={}", summarizeForLog(responseBody), exception);
			throw new LlmBackendException(LlmFailureType.PARSE_ERROR, "Failed to parse vision response", exception);
		}
	}

	private static String summarizeForLog(String text) {
		if (text == null) {
			return "";
		}
		String normalized = text
			.replace("\\", "\\\\")
			.replace("\r", "\\r")
			.replace("\n", "\\n");
		if (normalized.length() > 1200) {
			return normalized.substring(0, 1200) + "...";
		}
		return normalized;
	}
}
