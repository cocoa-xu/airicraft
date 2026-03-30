package ai.moeru.airicraft.agent.llm;

import ai.moeru.airicraft.BridgeUnavailableException;
import ai.moeru.airicraft.agent.AgentConfig;
import ai.moeru.airicraft.agent.goals.GoalType;
import ai.moeru.airicraft.agent.session.SessionMode;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

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
		PlannerOrchestrator orchestrator = new PlannerOrchestrator(
			new PlannerExecutor(backend),
			CurrentViewVisionTool.disabled()
		);

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
		PlannerOrchestrator orchestrator = new PlannerOrchestrator(
			new PlannerExecutor(backend),
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
		PlannerOrchestrator orchestrator = new PlannerOrchestrator(
			new PlannerExecutor(backend),
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
		PlannerOrchestrator orchestrator = new PlannerOrchestrator(
			new PlannerExecutor(backend),
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
		PlannerOrchestrator orchestrator = new PlannerOrchestrator(
			new PlannerExecutor(backend),
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

	private static PlannerRequest baseRequest(String toolResult) {
		return new PlannerRequest(
			10L,
			SessionMode.OUT_OF_WORLD,
			"Alice",
			null,
			List.of(),
			"Alice",
			"@agent what do you see?",
			toolResult
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
}
