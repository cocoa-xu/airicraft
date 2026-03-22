package ai.moeru.airicraft.wrapper;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HttpBridgeTransportTest {
	private String originalUserHome = System.getProperty("user.home");

	@AfterEach
	void restoreUserHome() {
		System.setProperty("user.home", originalUserHome);
	}

	@Test
	void getStatusFallsBackWhenBridgeIsUnavailable(@TempDir Path tempDir) throws Exception {
		writeBridgeState(tempDir);
		System.setProperty("user.home", tempDir.toString());

		HttpBridgeTransport transport = new HttpBridgeTransport();

		var status = transport.getStatus();

		assertEquals(false, status.get("available"));
		assertEquals("minecraft_unavailable", status.get("state"));
		assertFalse(Files.exists(tempDir.resolve(".airicraft/bridge-state.json")));
	}

	@Test
	void staleBridgeStateIsDeletedOnConnectFailure(@TempDir Path tempDir) throws Exception {
		writeBridgeState(tempDir);
		System.setProperty("user.home", tempDir.toString());

		HttpBridgeTransport transport = new HttpBridgeTransport();
		BridgeUnavailableException exception = assertThrows(BridgeUnavailableException.class, transport::listWorlds);

		assertEquals("minecraft_unavailable", exception.code());
	}

	@Test
	void joinWorldAllowsLongerBridgeResponse(@TempDir Path tempDir) throws Exception {
		try (TestBridgeServer server = TestBridgeServer.start()) {
			server.respondJson("/v1/worlds/join", 2500, 200, """
				{"started":true,"worldId":"test-world","name":"Test World","displayName":"Test World"}
				""");
			writeBridgeState(tempDir, server.port());
			System.setProperty("user.home", tempDir.toString());

			HttpBridgeTransport transport = new HttpBridgeTransport();

			Map<String, Object> payload = transport.joinWorld("test-world");

			assertEquals(true, payload.get("started"));
			assertEquals(1, server.requestCount("/v1/worlds/join"));
		}
	}

	@Test
	void nonJoinRequestsStillUseShortTimeout(@TempDir Path tempDir) throws Exception {
		try (TestBridgeServer server = TestBridgeServer.start()) {
			server.respondJson("/v1/focus", 2500, 200, """
				{"available":true,"worldLoaded":true}
				""");
			writeBridgeState(tempDir, server.port());
			System.setProperty("user.home", tempDir.toString());

			HttpBridgeTransport transport = new HttpBridgeTransport();
			BridgeUnavailableException exception = assertThrows(BridgeUnavailableException.class, transport::getFocus);

			assertEquals("bridge_io_error", exception.code());
			assertTrue(exception.getMessage().contains("timed out"));
		}
	}

	private static void writeBridgeState(Path tempDir) throws Exception {
		writeBridgeState(tempDir, 1);
	}

	private static void writeBridgeState(Path tempDir, int port) throws Exception {
		Path bridgeDir = tempDir.resolve(".airicraft");
		Files.createDirectories(bridgeDir);
		Files.writeString(bridgeDir.resolve("bridge-state.json"), """
			{
			  "port": %d,
			  "token": "test-token",
			  "startedAtEpochMillis": 1
			}
			""".formatted(port));
	}

	private static final class TestBridgeServer implements AutoCloseable {
		private final HttpServer server;
		private final Map<String, Integer> requestCounts = new java.util.concurrent.ConcurrentHashMap<>();

		private TestBridgeServer(HttpServer server) {
			this.server = server;
		}

		private static TestBridgeServer start() throws IOException {
			HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
			server.setExecutor(Executors.newCachedThreadPool());
			server.start();
			return new TestBridgeServer(server);
		}

		private int port() {
			return server.getAddress().getPort();
		}

		private void respondJson(String path, long delayMillis, int statusCode, String body) {
			server.createContext(path, exchange -> {
				requestCounts.merge(path, 1, Integer::sum);
				if (!"Bearer test-token".equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
					writeResponse(exchange, 401, "{\"error\":\"unauthorized\"}");
					return;
				}
				if (delayMillis > 0) {
					try {
						Thread.sleep(delayMillis);
					}
					catch (InterruptedException exception) {
						Thread.currentThread().interrupt();
					}
				}
				writeResponse(exchange, statusCode, body);
			});
		}

		private int requestCount(String path) {
			return requestCounts.getOrDefault(path, 0);
		}

		@Override
		public void close() {
			server.stop(0);
		}
	}

	private static void writeResponse(HttpExchange exchange, int statusCode, String body) throws IOException {
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().set("Content-Type", "application/json");
		exchange.sendResponseHeaders(statusCode, bytes.length);
		exchange.getResponseBody().write(bytes);
		exchange.close();
	}
}
