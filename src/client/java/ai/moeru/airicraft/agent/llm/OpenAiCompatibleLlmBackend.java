package ai.moeru.airicraft.agent.llm;

import ai.moeru.airicraft.Airicraft;
import ai.moeru.airicraft.agent.AgentConfig;
import ai.moeru.airicraft.agent.dialogue.DialogueSpeakerLabels;
import ai.moeru.airicraft.agent.dialogue.DialogueTurn;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
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
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

public final class OpenAiCompatibleLlmBackend implements LlmBackend {
	private static final Gson GSON = new Gson();
	// TODO: extract prompt to standalone config file
	private static final String SYSTEM_PROMPT = """
		You are the planner for a Minecraft companion. Return strict JSON with:
		{
		  "replyText": string,
		  "intent": {
		    "type": "set_goal" | "clear_goal" | "reply_only" | "ask_clarification" | "acknowledge_failure" | "none",
		    "goalType": "FOLLOW_PLAYER" | null,
		    "targetPlayer": string | null
		  },
		  "toolRequest": {
		    "type": "describe_current_view",
		    "prompt": string
		  } | null
		}
		Only choose FOLLOW_PLAYER when the player explicitly asks the companion to follow.
		If you need visual information, return toolRequest and set replyText to "" and intent.type to "none".
		When a tool result is already present in the prompt, do not request another tool.
		If a message comes from "%s", it is not another in-world player. It is the developer/admin on the very same client you run on, and they share controls with you.
		Treat messages from "%s" as operator instructions and high-priority local guidance.
		replyText must be a single plain Minecraft chat line.
		Keep replyText under 160 characters.
		Do not use markdown, code fences, bullet lists, decorative formatting, or multi-line text.
		Plain text is preferred. A light kaomoji or a single simple emoji is acceptable, but keep it sparse.
		Do not start replyText with a slash.
		Do not claim capabilities the companion does not actually have.
		""".formatted(DialogueSpeakerLabels.SAME_CLIENT_ADMIN, DialogueSpeakerLabels.SAME_CLIENT_ADMIN);

	private final AgentConfig.LlmConfig config;
	private final HttpClient httpClient = HttpClient.newHttpClient();
	private final Deque<Object> injectedOutcomes = new ArrayDeque<>();

	public OpenAiCompatibleLlmBackend(AgentConfig.LlmConfig config) {
		this.config = Objects.requireNonNull(config, "config");
	}

	@Override
	public synchronized PlannerResponse generate(PlannerRequest request) throws LlmBackendException {
		Objects.requireNonNull(request, "request");

		Object injected = injectedOutcomes.pollFirst();
		if (injected instanceof PlannerResponse plannerResponse) {
			return plannerResponse;
		}
		if (injected instanceof TimeoutException timeoutException) {
			throw new LlmBackendException(LlmFailureType.TIMEOUT, timeoutException.getMessage(), timeoutException);
		}

		if (!isConfigured()) {
			throw new LlmBackendException(LlmFailureType.PROVIDER_UNAVAILABLE, "LLM provider is not configured");
		}

		String prompt = renderPrompt(request);
		String requestBody = GSON.toJson(buildRequestPayload(prompt));
		Airicraft.LOGGER.info(
			"Planner request model={} sender={} turns={} toolResultPresent={} prompt={}",
			config.model(),
			request.senderName(),
			request.recentTurns().size(),
			request.toolResult() != null && !request.toolResult().isBlank(),
			summarizeForLog(prompt)
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
				"Planner response model={} status={} body={}",
				config.model(),
				response.statusCode(),
				summarizeForLog(response.body())
			);
			if (response.statusCode() >= 400) {
				throw new LlmBackendException(LlmFailureType.PROVIDER_ERROR, "Provider returned HTTP " + response.statusCode());
			}

			return parsePlannerResponse(response.body());
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

	@Override
	public synchronized void injectMockResponse(PlannerResponse response) {
		injectedOutcomes.addLast(Objects.requireNonNull(response, "response"));
	}

	@Override
	public synchronized void injectTimeout() {
		injectedOutcomes.addLast(new TimeoutException("Injected LLM timeout"));
	}

	@Override
	public boolean isConfigured() {
		return config.isConfigured();
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

	private Map<String, Object> buildRequestPayload(String prompt) {
		return Map.of(
			"model", config.model(),
			"response_format", Map.of("type", "json_object"),
			"messages", List.of(
				Map.of("role", "system", "content", SYSTEM_PROMPT),
				Map.of("role", "user", "content", prompt)
			)
		);
	}

	private String renderPrompt(PlannerRequest request) {
		StringBuilder builder = new StringBuilder();
		builder.append("Current session mode: ").append(request.sessionMode()).append('\n');
		builder.append("Primary interaction player: ").append(nullToEmpty(request.primaryInteractionPlayer())).append('\n');
		builder.append("Active goal: ");
		if (request.activeGoal() == null) {
			builder.append("none");
		}
		else {
			builder.append(request.activeGoal().type()).append(" target=").append(request.activeGoal().targetPlayer());
		}
		builder.append('\n');
		builder.append("Recent conversation:\n");
		for (DialogueTurn turn : request.recentTurns()) {
			builder.append("- ").append(turn.speaker()).append(": ").append(turn.text()).append('\n');
		}
		// TODO: Improve prompt construction, ask a human to do this.
		if (DialogueSpeakerLabels.isSameClientAdmin(request.senderName())) {
			builder.append("Latest message is from the developer/admin on this same client with you.\n");
		}
		builder.append("Latest message from ").append(request.senderName()).append(": ").append(request.message()).append('\n');
		builder.append("Tool result: ");
		if (request.toolResult() == null || request.toolResult().isBlank()) {
			builder.append("none");
		}
		else {
			builder.append(request.toolResult());
		}
		builder.append('\n');
		builder.append("Decide whether to set or clear a goal, and provide a concise plain-text reply that is safe to send in Minecraft chat.");
		return builder.toString();
	}

	private PlannerResponse parsePlannerResponse(String responseBody) throws LlmBackendException {
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

			String content = OpenAiCompatibleMessageContent.extract(message.get("content"));
			JsonObject payload = JsonParser.parseString(content).getAsJsonObject();
			String replyText = getString(payload, "replyText").orElse("");
			JsonObject intentObject = payload.has("intent") && payload.get("intent").isJsonObject()
				? payload.getAsJsonObject("intent")
				: new JsonObject();
			JsonObject toolRequestObject = payload.has("toolRequest") && payload.get("toolRequest").isJsonObject()
				? payload.getAsJsonObject("toolRequest")
				: null;

			PlannerIntent intent = new PlannerIntent(
				getString(intentObject, "type").orElse("none").toLowerCase(Locale.ROOT),
				getString(intentObject, "goalType")
					.map(value -> ai.moeru.airicraft.agent.goals.GoalType.valueOf(value.toUpperCase(Locale.ROOT)))
					.orElse(null),
				getString(intentObject, "targetPlayer").orElse(null)
			);
			PlannerToolRequest toolRequest = toolRequestObject == null
				? null
				: new PlannerToolRequest(
					getString(toolRequestObject, "type").orElse(null),
					getString(toolRequestObject, "prompt").orElse(null)
				);
			Airicraft.LOGGER.info(
				"Planner parsed response intentType={} goalType={} targetPlayer={} replyText={} toolRequestType={} toolPrompt={}",
				intent.type(),
				intent.goalType(),
				intent.targetPlayer(),
				summarizeForLog(replyText),
				toolRequest == null ? null : toolRequest.type(),
				toolRequest == null ? null : summarizeForLog(toolRequest.prompt())
			);
			return new PlannerResponse(replyText, intent, toolRequest);
		}
		catch (IllegalArgumentException | JsonParseException exception) {
			Airicraft.LOGGER.warn("Failed to parse planner response body={}", summarizeForLog(responseBody), exception);
			throw new LlmBackendException(LlmFailureType.PARSE_ERROR, "Failed to parse planner response", exception);
		}
	}

	private static Optional<String> getString(JsonObject object, String fieldName) {
		if (object == null || !object.has(fieldName) || object.get(fieldName).isJsonNull()) {
			return Optional.empty();
		}
		String value = object.get(fieldName).getAsString();
		return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
	}

	private static String nullToEmpty(String value) {
		return value == null ? "" : value;
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
