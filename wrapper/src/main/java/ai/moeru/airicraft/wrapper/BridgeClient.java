package ai.moeru.airicraft.wrapper;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

final class BridgeClient {
	private final HttpClient httpClient = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(1))
		.build();

	String getStatusText() {
		try {
			return get("/v1/status");
		}
		catch (BridgeUnavailableException exception) {
			return "{\"available\":false,\"worldLoaded\":false,\"state\":\"minecraft_unavailable\",\"message\":\"Minecraft bridge is not active\"}";
		}
	}

	String getFocus() {
		return get("/v1/focus");
	}

	String getWorldSnapshot(Integer x, Integer y, Integer z, int radius) {
		StringBuilder path = new StringBuilder("/v1/world-snapshot?radius=").append(radius);
		if (x != null && y != null && z != null) {
			path.append("&x=").append(x).append("&y=").append(y).append("&z=").append(z);
		}
		return get(path.toString());
	}

	String createHighlight(int x, int y, int z, String color, long durationMs) {
		return send("POST", "/v1/highlights", "{\"x\":" + x + ",\"y\":" + y + ",\"z\":" + z
			+ ",\"color\":\"" + escape(color) + "\",\"durationMs\":" + durationMs + "}");
	}

	String clearHighlights() {
		return send("DELETE", "/v1/highlights", null);
	}

	private String get(String path) {
		return send("GET", path, null);
	}

	private String send(String method, String path, String body) {
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
				builder.method(method, HttpRequest.BodyPublishers.ofString(body))
					.header("Content-Type", "application/json");
			}

			HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() >= 400) {
				throw new BridgeUnavailableException("bridge_error", nonEmpty(response.body(), "Bridge request failed"));
			}

			return nonEmpty(response.body(), "{}");
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

	private static String escape(String value) {
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	private static String nonEmpty(String value, String fallback) {
		return value == null || value.isBlank() ? fallback : value;
	}
}
