package ai.moeru.airicraft.agent.llm;

import ai.moeru.airicraft.agent.AgentConfig;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiCompatibleLlmBackendTest {
	@Test
	void generateParsesPlannerResponseAndUsage() throws Exception {
		AtomicReference<String> bodyRef = new AtomicReference<>();
		try (TestServer server = TestServer.start(bodyRef)) {
			OpenAiCompatibleLlmBackend backend = new OpenAiCompatibleLlmBackend(new AgentConfig.LlmConfig(
				"http://127.0.0.1:" + server.port(),
				"planner-key",
				"planner-model",
				"https://api.openai.com/v1",
				"",
				"",
				15_000,
				10_000,
				8,
				65_536,
				"low"
			));

			LlmCallResult<PlannerResponse> result = backend.generate(LlmConversation.of(List.of(
				LlmChatMessage.system("system"),
				LlmChatMessage.user("Alice said just now: @agent follow me", LlmMessageKind.USER_TURN)
			)));

			assertEquals("Sure, I'll follow you.", result.payload().replyText());
			assertEquals("set_goal", result.payload().intent().type());
			assertEquals(Integer.valueOf(1234), result.usage().promptTokens());
			assertEquals(Integer.valueOf(56), result.usage().completionTokens());
			assertEquals(Integer.valueOf(1290), result.usage().totalTokens());

			String body = bodyRef.get();
			assertTrue(body.contains("\"role\":\"system\""));
			assertTrue(body.contains("Alice said just now"));
		}
	}

	private static final class TestServer implements AutoCloseable {
		private final HttpServer server;

		private TestServer(HttpServer server) {
			this.server = server;
		}

		private static TestServer start(AtomicReference<String> bodyRef) throws IOException {
			HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
			server.setExecutor(Executors.newCachedThreadPool());
			server.createContext("/chat/completions", exchange -> handle(exchange, bodyRef));
			server.start();
			return new TestServer(server);
		}

		private static void handle(HttpExchange exchange, AtomicReference<String> bodyRef) throws IOException {
			bodyRef.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			writeResponse(exchange, 200, """
				{
				  "choices": [
				    {
				      "message": {
				        "content": "{\\"replyText\\":\\"Sure, I'll follow you.\\",\\"intent\\":{\\"type\\":\\"set_goal\\",\\"goalType\\":\\"FOLLOW_PLAYER\\",\\"targetPlayer\\":\\"Alice\\"},\\"toolRequest\\":null}"
				      }
				    }
				  ],
				  "usage": {
				    "prompt_tokens": 1234,
				    "completion_tokens": 56,
				    "total_tokens": 1290
				  }
				}
				""");
		}

		private int port() {
			return server.getAddress().getPort();
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
