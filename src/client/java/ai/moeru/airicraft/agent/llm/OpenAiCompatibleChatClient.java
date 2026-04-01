package ai.moeru.airicraft.agent.llm;

import ai.moeru.airicraft.Airicraft;
import ai.moeru.airicraft.agent.AgentConfig;
import com.google.gson.Gson;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class OpenAiCompatibleChatClient {
	private static final Gson GSON = new Gson();

	private final AgentConfig.LlmConfig config;
	private final HttpClient httpClient = HttpClient.newHttpClient();

	public OpenAiCompatibleChatClient(AgentConfig.LlmConfig config) {
		this.config = Objects.requireNonNull(config, "config");
	}

	LlmCallResult<String> complete(LlmConversation conversation) throws LlmBackendException {
		Objects.requireNonNull(conversation, "conversation");
		if (!config.isConfigured()) {
			throw new LlmBackendException(LlmFailureType.PROVIDER_UNAVAILABLE, "LLM provider is not configured");
		}

		String requestBody = GSON.toJson(buildRequestPayload(conversation));
		Airicraft.LOGGER.info(
			"LLM request model={} messages={} preview={}",
			config.model(),
			conversation.messages().size(),
			summarizeConversation(conversation)
		);
		HttpRequest httpRequest = HttpRequest.newBuilder()
			.uri(buildUri())
			.timeout(Duration.ofMillis(config.requestTimeoutMillis()))
			.header("Authorization", "Bearer " + config.apiKey())
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
			.build();

		try {
			HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			Airicraft.LOGGER.info(
				"LLM response model={} status={} body={}",
				config.model(),
				response.statusCode(),
				summarizeForLog(response.body())
			);
			if (response.statusCode() >= 400) {
				throw new LlmBackendException(LlmFailureType.PROVIDER_ERROR, "Provider returned HTTP " + response.statusCode());
			}
			return LlmCallResult.of(response.body(), parseUsage(response.body()));
		}
		catch (java.net.http.HttpTimeoutException exception) {
			throw new LlmBackendException(LlmFailureType.TIMEOUT, "LLM request timed out", exception);
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new LlmBackendException(LlmFailureType.TIMEOUT, "LLM request interrupted", exception);
		}
		catch (IOException exception) {
			throw new LlmBackendException(LlmFailureType.PROVIDER_ERROR, "LLM request failed", exception);
		}
	}

	private URI buildUri() throws LlmBackendException {
		try {
			String baseUrl = config.providerBaseUrl().endsWith("/")
				? config.providerBaseUrl().substring(0, config.providerBaseUrl().length() - 1)
				: config.providerBaseUrl();
			return URI.create(baseUrl + "/chat/completions");
		}
		catch (IllegalArgumentException exception) {
			throw new LlmBackendException(LlmFailureType.PROVIDER_UNAVAILABLE, "Invalid LLM provider URL", exception);
		}
	}

	private Map<String, Object> buildRequestPayload(LlmConversation conversation) {
		return Map.of(
			"model", config.model(),
			"response_format", Map.of("type", "json_object"),
			"messages", conversation.messages().stream().map(this::toRequestMessage).toList()
		);
	}

	private Map<String, Object> toRequestMessage(LlmChatMessage message) {
		LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
		payload.put("role", message.role());
		payload.put("content", message.content());
		return payload;
	}

	private static LlmUsageSnapshot parseUsage(String responseBody) {
		try {
			JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();
			if (!root.has("usage") || !root.get("usage").isJsonObject()) {
				return LlmUsageSnapshot.unknown();
			}
			JsonObject usage = root.getAsJsonObject("usage");
			Integer promptTokens = getUsageInt(usage, "prompt_tokens");
			if (promptTokens == null) {
				promptTokens = getUsageInt(usage, "input_tokens");
			}
			Integer completionTokens = getUsageInt(usage, "completion_tokens");
			if (completionTokens == null) {
				completionTokens = getUsageInt(usage, "output_tokens");
			}
			Integer totalTokens = getUsageInt(usage, "total_tokens");
			return new LlmUsageSnapshot(promptTokens, completionTokens, totalTokens);
		}
		catch (IllegalStateException | JsonParseException exception) {
			return LlmUsageSnapshot.unknown();
		}
	}

	private static Integer getUsageInt(JsonObject usage, String fieldName) {
		if (usage == null || !usage.has(fieldName) || usage.get(fieldName).isJsonNull()) {
			return null;
		}
		return usage.get(fieldName).getAsInt();
	}

	private static String summarizeConversation(LlmConversation conversation) {
		StringBuilder builder = new StringBuilder();
		for (LlmChatMessage message : conversation.messages()) {
			if (!builder.isEmpty()) {
				builder.append(" | ");
			}
			builder.append(message.role()).append(':').append(summarizeForLog(message.content()));
		}
		return summarizeForLog(builder.toString());
	}

	static String summarizeForLog(String text) {
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
