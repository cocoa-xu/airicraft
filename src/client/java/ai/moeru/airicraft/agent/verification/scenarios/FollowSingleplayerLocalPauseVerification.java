package ai.moeru.airicraft.agent.verification.scenarios;

import ai.moeru.airicraft.agent.verification.ScenarioBuilder;
import ai.moeru.airicraft.agent.verification.VerificationScenario;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public final class FollowSingleplayerLocalPauseVerification extends VerificationScenario {
	private final BooleanSupplier singleplayerLocal;
	private final Runnable setupMockPlannerResponse;
	private final Runnable injectNearbyPlayer;
	private final Runnable injectFollowRequest;
	private final Supplier<Boolean> dialogueResponseAvailable;
	private final Supplier<Boolean> setGoalIntentSeen;
	private final BooleanSupplier targetAcquiredSeen;
	private final Supplier<Boolean> followGoalActive;
	private final BooleanSupplier actuationBlockedSeen;
	private final BooleanSupplier movementIdle;

	public FollowSingleplayerLocalPauseVerification(
		BooleanSupplier singleplayerLocal,
		Runnable setupMockPlannerResponse,
		Runnable injectNearbyPlayer,
		Runnable injectFollowRequest,
		Supplier<Boolean> dialogueResponseAvailable,
		Supplier<Boolean> setGoalIntentSeen,
		BooleanSupplier targetAcquiredSeen,
		Supplier<Boolean> followGoalActive,
		BooleanSupplier actuationBlockedSeen,
		BooleanSupplier movementIdle
	) {
		this.singleplayerLocal = Objects.requireNonNull(singleplayerLocal, "singleplayerLocal");
		this.setupMockPlannerResponse = Objects.requireNonNull(setupMockPlannerResponse, "setupMockPlannerResponse");
		this.injectNearbyPlayer = Objects.requireNonNull(injectNearbyPlayer, "injectNearbyPlayer");
		this.injectFollowRequest = Objects.requireNonNull(injectFollowRequest, "injectFollowRequest");
		this.dialogueResponseAvailable = Objects.requireNonNull(dialogueResponseAvailable, "dialogueResponseAvailable");
		this.setGoalIntentSeen = Objects.requireNonNull(setGoalIntentSeen, "setGoalIntentSeen");
		this.targetAcquiredSeen = Objects.requireNonNull(targetAcquiredSeen, "targetAcquiredSeen");
		this.followGoalActive = Objects.requireNonNull(followGoalActive, "followGoalActive");
		this.actuationBlockedSeen = Objects.requireNonNull(actuationBlockedSeen, "actuationBlockedSeen");
		this.movementIdle = Objects.requireNonNull(movementIdle, "movementIdle");
	}

	@Override
	public String name() {
		return "follow.singleplayer_local_pause";
	}

	@Override
	protected void define(ScenarioBuilder builder) {
		builder
			.require("in singleplayer local", singleplayerLocal)
			.action("setup mock planner response", setupMockPlannerResponse)
			.action("inject nearby player", injectNearbyPlayer)
			.action("inject follow request", injectFollowRequest)
			.waitUntil("dialogue response available", 300, dialogueResponseAvailable::get)
			.assertThat("set goal intent seen", setGoalIntentSeen::get)
			.waitUntil("follow target acquired", 200, targetAcquiredSeen)
			.assertThat("follow goal active", followGoalActive::get)
			.waitUntil("actuation blocked by session", 100, actuationBlockedSeen)
			.assertThat("movement stays idle", movementIdle);
	}
}
