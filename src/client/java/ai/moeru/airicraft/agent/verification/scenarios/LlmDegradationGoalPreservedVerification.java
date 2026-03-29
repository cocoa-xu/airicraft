package ai.moeru.airicraft.agent.verification.scenarios;

import ai.moeru.airicraft.agent.verification.ScenarioBuilder;
import ai.moeru.airicraft.agent.verification.VerificationScenario;

import java.util.Objects;
import java.util.function.BooleanSupplier;

public final class LlmDegradationGoalPreservedVerification extends VerificationScenario {
	private final BooleanSupplier worldLoaded;
	private final Runnable injectNearbyPlayer;
	private final Runnable setupFollowResponse;
	private final Runnable injectFollowRequest;
	private final BooleanSupplier followGoalActive;
	private final Runnable triggerTimeoutOne;
	private final Runnable triggerTimeoutTwo;
	private final Runnable triggerTimeoutThree;
	private final BooleanSupplier degraded;
	private final BooleanSupplier goalStillActive;
	private final BooleanSupplier behaviorTreeActive;
	private final Runnable requestReset;
	private final BooleanSupplier recovered;

	public LlmDegradationGoalPreservedVerification(
		BooleanSupplier worldLoaded,
		Runnable injectNearbyPlayer,
		Runnable setupFollowResponse,
		Runnable injectFollowRequest,
		BooleanSupplier followGoalActive,
		Runnable triggerTimeoutOne,
		Runnable triggerTimeoutTwo,
		Runnable triggerTimeoutThree,
		BooleanSupplier degraded,
		BooleanSupplier goalStillActive,
		BooleanSupplier behaviorTreeActive,
		Runnable requestReset,
		BooleanSupplier recovered
	) {
		this.worldLoaded = Objects.requireNonNull(worldLoaded, "worldLoaded");
		this.injectNearbyPlayer = Objects.requireNonNull(injectNearbyPlayer, "injectNearbyPlayer");
		this.setupFollowResponse = Objects.requireNonNull(setupFollowResponse, "setupFollowResponse");
		this.injectFollowRequest = Objects.requireNonNull(injectFollowRequest, "injectFollowRequest");
		this.followGoalActive = Objects.requireNonNull(followGoalActive, "followGoalActive");
		this.triggerTimeoutOne = Objects.requireNonNull(triggerTimeoutOne, "triggerTimeoutOne");
		this.triggerTimeoutTwo = Objects.requireNonNull(triggerTimeoutTwo, "triggerTimeoutTwo");
		this.triggerTimeoutThree = Objects.requireNonNull(triggerTimeoutThree, "triggerTimeoutThree");
		this.degraded = Objects.requireNonNull(degraded, "degraded");
		this.goalStillActive = Objects.requireNonNull(goalStillActive, "goalStillActive");
		this.behaviorTreeActive = Objects.requireNonNull(behaviorTreeActive, "behaviorTreeActive");
		this.requestReset = Objects.requireNonNull(requestReset, "requestReset");
		this.recovered = Objects.requireNonNull(recovered, "recovered");
	}

	@Override
	public String name() {
		return "llm.degradation_goal_preserved";
	}

	@Override
	protected void define(ScenarioBuilder builder) {
		builder
			.require("in world", worldLoaded)
			.action("inject nearby player", injectNearbyPlayer)
			.action("setup follow response", setupFollowResponse)
			.action("inject follow request", injectFollowRequest)
			.waitUntil("follow goal active", 300, followGoalActive)
			.action("trigger timeout 1", triggerTimeoutOne)
			.action("trigger timeout 2", triggerTimeoutTwo)
			.action("trigger timeout 3", triggerTimeoutThree)
			.waitUntil("runtime enters degraded", 100, degraded)
			.assertThat("follow goal remains active", goalStillActive)
			.assertThat("behavior tree remains active", behaviorTreeActive)
			.action("request reset", requestReset)
			.waitUntil("runtime recovers", 20, recovered)
			.assertThat("follow goal remains active after reset", goalStillActive);
	}
}
