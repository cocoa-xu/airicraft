package ai.moeru.airicraft.agent.llm;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class PlannerExecutor {
	private final LlmBackend llmBackend;
	private final ExecutorService executorService;

	private CompletableFuture<PlannerResponse> inFlight;
	private PlannerRequest inFlightRequest;

	public PlannerExecutor(LlmBackend llmBackend) {
		this.llmBackend = Objects.requireNonNull(llmBackend, "llmBackend");
		this.executorService = Executors.newSingleThreadExecutor(runnable -> {
			Thread thread = new Thread(runnable, "airicraft-planner");
			thread.setDaemon(true);
			return thread;
		});
	}

	public boolean isConfigured() {
		return llmBackend.isConfigured();
	}

	public boolean hasInFlight() {
		return inFlight != null;
	}

	public boolean submit(PlannerRequest request) {
		Objects.requireNonNull(request, "request");
		if (inFlight != null) {
			return false;
		}

		inFlightRequest = request;
		inFlight = CompletableFuture.supplyAsync(() -> {
			try {
				return llmBackend.generate(request);
			}
			catch (LlmBackendException exception) {
				throw new CompletionException(exception);
			}
		}, executorService);
		return true;
	}

	public PlannerExecutionResult poll() {
		if (inFlight == null || !inFlight.isDone()) {
			return null;
		}

		PlannerRequest request = inFlightRequest;
		CompletableFuture<PlannerResponse> completedFuture = inFlight;
		inFlight = null;
		inFlightRequest = null;

		try {
			return new PlannerExecutionResult(request, completedFuture.join(), null, null);
		}
		catch (CompletionException exception) {
			Throwable cause = exception.getCause();
			if (cause instanceof LlmBackendException backendException) {
				return new PlannerExecutionResult(request, null, backendException.failureType(), backendException.getMessage());
			}
			return new PlannerExecutionResult(
				request,
				null,
				LlmFailureType.PROVIDER_ERROR,
				cause == null ? exception.getMessage() : cause.getMessage()
			);
		}
	}

	public void injectMockResponse(PlannerResponse response) {
		llmBackend.injectMockResponse(response);
	}

	public void injectTimeout() {
		llmBackend.injectTimeout();
	}

	public void reset() {
		if (inFlight != null) {
			inFlight.cancel(true);
			inFlight = null;
			inFlightRequest = null;
		}
	}

	public void shutdown() {
		reset();
		executorService.shutdownNow();
	}
}
