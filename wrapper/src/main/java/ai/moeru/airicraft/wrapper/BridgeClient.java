package ai.moeru.airicraft.wrapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

final class BridgeClient {
	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
	private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() {
	};

	private final HttpClient httpClient = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(1))
		.build();

	Map<String, Object> getStatus() {
		try {
			return get("/v1/status");
		}
		catch (BridgeUnavailableException exception) {
			return Map.of(
				"available", false,
				"worldLoaded", false,
				"state", "minecraft_unavailable",
				"message", "Minecraft bridge is not active"
			);
		}
	}

	Map<String, Object> getFocus() {
		return get("/v1/focus");
	}

	Map<String, Object> getWorldSnapshot(Integer x, Integer y, Integer z, int radius) {
		StringBuilder path = new StringBuilder("/v1/world-snapshot?radius=").append(radius);
		if (x != null && y != null && z != null) {
			path.append("&x=").append(x).append("&y=").append(y).append("&z=").append(z);
		}
		return get(path.toString());
	}

	Map<String, Object> createHighlight(int x, int y, int z, String color, long durationMs) {
		return send("POST", "/v1/highlights", Map.of(
			"x", x,
			"y", y,
			"z", z,
			"color", color,
			"durationMs", durationMs
		));
	}

	Map<String, Object> clearHighlights() {
		return send("DELETE", "/v1/highlights", null);
	}

	private Map<String, Object> get(String path) {
		return send("GET", path, null);
	}

	private Map<String, Object> send(String method, String path, Object body) {
		BridgeStateFile.BridgeState state = BridgeStateFile.read()
			.orElseThrow(() -> new BridgeUnavailableException("minecraft_unavailable", "Minecraft bridge is not active"));

		HttpRequest.Builder builder = HttpRequest.newBuilder()
			.uri(URI.create("http://127.0.0.1:" + state.port() + path))
			.timeout(Duration.ofSeconds(2))
			.header("Authorization", "Bearer " + state.token())
			.header("Accept", "application/json");

		try {
			if (body == null) {
				builder.method(method, HttpRequest.BodyPublishers.noBody());
			}
			else {
				builder.method(method, HttpRequest.BodyPublishers.ofString(OBJECT_MAPPER.writeValueAsString(body)))
					.header("Content-Type", "application/json");
			}

			HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
			Map<String, Object> payload = response.body() == null || response.body().isBlank()
				? new LinkedHashMap<>()
				: OBJECT_MAPPER.readValue(response.body(), MAP_TYPE);

			if (response.statusCode() >= 400) {
				String code = String.valueOf(payload.getOrDefault("error", "bridge_error"));
				String message = String.valueOf(payload.getOrDefault("message", "Bridge request failed"));
				throw new BridgeUnavailableException(code, message);
			}

			return payload;
		}
		catch (ConnectException exception) {
			throw new BridgeUnavailableException("minecraft_unavailable", "Minecraft bridge is not reachable");
		}
		catch (IOException exception) {
			throw new BridgeUnavailableException("bridge_io_error", nonEmpty(exception.getMessage(), "Bridge IO error"));
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new BridgeUnavailableException("bridge_interrupted", "Bridge request interrupted");
		}
	}

	private static String nonEmpty(String value, String fallback) {
		return value == null || value.isBlank() ? fallback : value;
	}
}
