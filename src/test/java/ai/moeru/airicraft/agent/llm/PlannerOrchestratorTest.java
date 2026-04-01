package ai.moeru.airicraft.agent.llm;

import ai.moeru.airicraft.BridgeUnavailableException;
import ai.moeru.airicraft.agent.AgentConfig;
import ai.moeru.airicraft.agent.dialogue.DialogueTurn;
import ai.moeru.airicraft.agent.goals.GoalType;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import ai.moeru.airicraft.agent.session.SessionMode;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlannerOrchestratorTest {
	@Test
	void returnsImmediatePlannerResponseWhenNoToolIsRequested() {
		OpenAiCompatibleLlmBackend backend = new OpenAiCompatibleLlmBackend(AgentConfig.LlmConfig.defaults());
		backend.injectMockResponse(new PlannerResponse(
			"Sure, I'll follow you.",
			new PlannerIntent("set_goal", GoalType.FOLLOW_PLAYER, "Alice")
		));
		PlannerOrchestrator orchestrator = newOrchestrator(backend, CurrentViewVisionTool.disabled());

		orchestrator.submit(baseRequest(null));
		PlannerExecutionResult result = awaitResult(orchestrator);

		assertNotNull(result);
		assertTrue(result.succeeded());
		assertEquals("Sure, I'll follow you.", result.response().replyText());
	}

	@Test
	void singleToolCallFeedsVisionDescriptionBackIntoPlanner() {
		OpenAiCompatibleLlmBackend backend = new OpenAiCompatibleLlmBackend(AgentConfig.LlmConfig.defaults());
		backend.injectMockResponse(new PlannerResponse(
			"",
			new PlannerIntent("none", null, null),
			new PlannerToolRequest("describe_current_view", "Describe the scene.")
		));
		backend.injectMockResponse(new PlannerResponse(
			"I see a forested hill ahead.",
			new PlannerIntent("reply_only", null, null)
		));
		PlannerOrchestrator orchestrator = newOrchestrator(
			backend,
			new StubVisionTool(CompletableFuture.completedFuture(new VisionDescription(
				"A birch forest hill under open sky.",
				"gpt-4.1-mini",
				1L
			)))
		);

		orchestrator.submit(baseRequest(null));
		PlannerExecutionResult result = awaitResult(orchestrator);

		assertNotNull(result);
		assertTrue(result.succeeded());
		assertEquals("I see a forested hill ahead.", result.response().replyText());
		assertEquals("A birch forest hill under open sky.", result.request().toolResult());
	}

	@Test
	void toolRequestIgnoresStrayReplyTextWhenIntentIsNone() {
		OpenAiCompatibleLlmBackend backend = new OpenAiCompatibleLlmBackend(AgentConfig.LlmConfig.defaults());
		backend.injectMockResponse(new PlannerResponse(
			"I dont see anything yet, where are you?",
			new PlannerIntent("none", null, null),
			new PlannerToolRequest("describe_current_view", "Describe the scene.")
		));
		backend.injectMockResponse(new PlannerResponse(
			"I can see a beach and ocean nearby.",
			new PlannerIntent("reply_only", null, null)
		));
		PlannerOrchestrator orchestrator = newOrchestrator(
			backend,
			new StubVisionTool(CompletableFuture.completedFuture(new VisionDescription(
				"A sandy beach next to the ocean under open sky.",
				"gpt-4.1-mini",
				1L
			)))
		);

		orchestrator.submit(baseRequest(null));
		PlannerExecutionResult result = awaitResult(orchestrator);

		assertNotNull(result);
		assertTrue(result.succeeded());
		assertEquals("I can see a beach and ocean nearby.", result.response().replyText());
		assertEquals("A sandy beach next to the ocean under open sky.", result.request().toolResult());
	}

	@Test
	void toolFailureFallsBackToSyntheticUnavailableMarker() {
		OpenAiCompatibleLlmBackend backend = new OpenAiCompatibleLlmBackend(AgentConfig.LlmConfig.defaults());
		backend.injectMockResponse(new PlannerResponse(
			"",
			new PlannerIntent("none", null, null),
			new PlannerToolRequest("describe_current_view", "Describe the scene.")
		));
		backend.injectMockResponse(new PlannerResponse(
			"I can't see clearly right now.",
			new PlannerIntent("acknowledge_failure", null, null)
		));
		PlannerOrchestrator orchestrator = newOrchestrator(
			backend,
			new StubVisionTool(CompletableFuture.failedFuture(
				new BridgeUnavailableException("capture_timeout", "Screenshot capture timed out")
			))
		);

		orchestrator.submit(baseRequest(null));
		PlannerExecutionResult result = awaitResult(orchestrator);

		assertNotNull(result);
		assertTrue(result.succeeded());
		assertEquals("VISION_UNAVAILABLE: capture_timeout", result.request().toolResult());
	}

	@Test
	void secondToolRequestReturnsParseFailure() {
		OpenAiCompatibleLlmBackend backend = new OpenAiCompatibleLlmBackend(AgentConfig.LlmConfig.defaults());
		backend.injectMockResponse(new PlannerResponse(
			"",
			new PlannerIntent("none", null, null),
			new PlannerToolRequest("describe_current_view", "Describe the scene.")
		));
		backend.injectMockResponse(new PlannerResponse(
			"",
			new PlannerIntent("none", null, null),
			new PlannerToolRequest("describe_current_view", "Describe the scene again.")
		));
		PlannerOrchestrator orchestrator = newOrchestrator(
			backend,
			new StubVisionTool(CompletableFuture.completedFuture(new VisionDescription(
				"A birch forest hill under open sky.",
				"gpt-4.1-mini",
				1L
			)))
		);

		orchestrator.submit(baseRequest(null));
		PlannerExecutionResult result = awaitResult(orchestrator);

		assertNotNull(result);
		assertEquals(LlmFailureType.PARSE_ERROR, result.failureType());
		assertTrue(result.failureMessage().contains("more than once"));
		assertNull(result.response());
	}

	@Test
	void debugCompactionCompletesWithoutPlannerRequest() throws Exception {
		try (CompactionTestServer server = CompactionTestServer.start()) {
			AgentConfig.LlmConfig config = new AgentConfig.LlmConfig(
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
			);
			PlannerOrchestrator orchestrator = new PlannerOrchestrator(
				new PlannerExecutor(new OpenAiCompatibleLlmBackend(config)),
				new PlannerCompactionService(new OpenAiCompatibleChatClient(config)),
				new PlannerContextAggregator(Clock.systemDefaultZone(), config.plannerCompactionTriggerTokens()),
				CurrentViewVisionTool.disabled()
			);
			orchestrator.recordAssistantTurn(new DialogueTurn("agent", "On it.", 10L, 1_000L));

			assertTrue(orchestrator.startDebugCompaction());
			awaitDebugCompaction(orchestrator);

			PlannerOrchestratorDebugSnapshot snapshot = orchestrator.debugSnapshot();
			assertTrue(snapshot.lastCompactionResult().succeeded());
			assertEquals("follow Alice", snapshot.context().activeCheckpoint().activeGoal());
			assertEquals(1, server.requestCount());
		}
	}

	private static PlannerRequest baseRequest(String toolResult) {
		return new PlannerRequest(
			10L,
			1_000L,
			SessionMode.OUT_OF_WORLD,
			"Alice",
			null,
			"Alice",
			"@agent what do you see?",
			toolResult
		);
	}

	private static PlannerOrchestrator newOrchestrator(OpenAiCompatibleLlmBackend backend, CurrentViewVisionTool visionTool) {
		AgentConfig.LlmConfig config = AgentConfig.LlmConfig.defaults();
		return new PlannerOrchestrator(
			new PlannerExecutor(backend),
			new PlannerCompactionService(new OpenAiCompatibleChatClient(config)),
			new PlannerContextAggregator(Clock.systemDefaultZone(), config.plannerCompactionTriggerTokens()),
			visionTool
		);
	}

	private static PlannerExecutionResult awaitResult(PlannerOrchestrator orchestrator) {
		Instant deadline = Instant.now().plus(Duration.ofSeconds(1));
		while (Instant.now().isBefore(deadline)) {
			PlannerExecutionResult result = orchestrator.poll();
			if (result != null) {
				return result;
			}
			try {
				Thread.sleep(10L);
			}
			catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
				throw new AssertionError("Interrupted while waiting", exception);
			}
		}
		throw new AssertionError("Timed out waiting for planner result");
	}

	private static void awaitDebugCompaction(PlannerOrchestrator orchestrator) {
		Instant deadline = Instant.now().plus(Duration.ofSeconds(2));
		while (Instant.now().isBefore(deadline)) {
			orchestrator.poll();
			PlannerOrchestratorDebugSnapshot snapshot = orchestrator.debugSnapshot();
			if (!snapshot.compactionInFlight() && snapshot.lastCompactionResult() != null) {
				return;
			}
			try {
				Thread.sleep(10L);
			}
			catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
				throw new AssertionError("Interrupted while waiting", exception);
			}
		}
		throw new AssertionError("Timed out waiting for debug compaction");
	}

	private record StubVisionTool(CompletableFuture<VisionDescription> future) implements CurrentViewVisionTool {
		@Override
		public boolean isConfigured() {
			return true;
		}

		@Override
		public CompletableFuture<VisionDescription> requestDescription(String prompt) {
			return future;
		}
	}

	private static final class CompactionTestServer implements AutoCloseable {
		private final HttpServer server;
		private int requestCount;

		private CompactionTestServer(HttpServer server) {
			this.server = server;
		}

		private static CompactionTestServer start() throws IOException {
			HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
			CompactionTestServer holder = new CompactionTestServer(server);
			server.setExecutor(Executors.newCachedThreadPool());
			server.createContext("/chat/completions", exchange -> holder.handle(exchange));
			server.start();
			return holder;
		}

		private void handle(HttpExchange exchange) throws IOException {
			requestCount++;
			writeResponse(exchange, 200, """
				{
				  "choices": [
				    {
				      "message": {
				        "content": "{\\"time_anchor\\":\\"Tuesday afternoon\\",\\"session_state\\":\\"in world\\",\\"active_goal\\":\\"follow Alice\\",\\"active_commitments\\":[\\"follow Alice\\"],\\"durable_facts\\":[\\"Alice is nearby\\"],\\"relevant_people\\":[\\"Alice\\"],\\"open_loops\\":[\\"keep following\\"],\\"recent_timeline\\":[\\"Alice asked for follow\\"],\\"forgettable_noise\\":[]}"
				      }
				    }
				  ],
				  "usage": {
				    "prompt_tokens": 2048,
				    "completion_tokens": 128,
				    "total_tokens": 2176
				  }
				}
				""");
		}

		private int port() {
			return server.getAddress().getPort();
		}

		private int requestCount() {
			return requestCount;
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
