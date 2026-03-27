package ai.moeru.airicraft.agent.verification.scenarios;

import ai.moeru.airicraft.agent.dialogue.DialogueIntentType;
import ai.moeru.airicraft.agent.goals.GoalType;
import ai.moeru.airicraft.agent.verification.ScenarioBuilder;
import ai.moeru.airicraft.agent.verification.VerificationScenario;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public final class FollowVerification extends VerificationScenario {
	private final BooleanSupplier worldLoaded;
	private final Runnable injectNearbyPlayer;
	private final Runnable injectFollowRequest;
	private final Supplier<Boolean> dialogueResponseAvailable;
	private final Supplier<Boolean> setGoalIntentSeen;
	private final BooleanSupplier targetAcquiredSeen;
	private final Supplier<Boolean> followGoalActive;
	private final Runnable injectFarMove;
	private final Supplier<Boolean> moveCloserActive;
	private final Runnable injectDisconnect;
	private final BooleanSupplier targetLostSeen;

	public FollowVerification(
		BooleanSupplier worldLoaded,
		Runnable injectNearbyPlayer,
		Runnable injectFollowRequest,
		Supplier<Boolean> dialogueResponseAvailable,
		Supplier<Boolean> setGoalIntentSeen,
		BooleanSupplier targetAcquiredSeen,
		Supplier<Boolean> followGoalActive,
		Runnable injectFarMove,
		Supplier<Boolean> moveCloserActive,
		Runnable injectDisconnect,
		BooleanSupplier targetLostSeen
	) {
		this.worldLoaded = Objects.requireNonNull(worldLoaded, "worldLoaded");
		this.injectNearbyPlayer = Objects.requireNonNull(injectNearbyPlayer, "injectNearbyPlayer");
		this.injectFollowRequest = Objects.requireNonNull(injectFollowRequest, "injectFollowRequest");
		this.dialogueResponseAvailable = Objects.requireNonNull(dialogueResponseAvailable, "dialogueResponseAvailable");
		this.setGoalIntentSeen = Objects.requireNonNull(setGoalIntentSeen, "setGoalIntentSeen");
		this.targetAcquiredSeen = Objects.requireNonNull(targetAcquiredSeen, "targetAcquiredSeen");
		this.followGoalActive = Objects.requireNonNull(followGoalActive, "followGoalActive");
		this.injectFarMove = Objects.requireNonNull(injectFarMove, "injectFarMove");
		this.moveCloserActive = Objects.requireNonNull(moveCloserActive, "moveCloserActive");
		this.injectDisconnect = Objects.requireNonNull(injectDisconnect, "injectDisconnect");
		this.targetLostSeen = Objects.requireNonNull(targetLostSeen, "targetLostSeen");
	}

	@Override
	public String name() {
		return "follow.basic";
	}

	@Override
	protected void define(ScenarioBuilder builder) {
		builder
			.require("in world", worldLoaded)
			.action("inject nearby player", injectNearbyPlayer)
			.action("inject follow request", injectFollowRequest)
			.waitUntil("dialogue response available", 300, dialogueResponseAvailable::get)
			.assertThat("set goal intent seen", setGoalIntentSeen::get)
			.waitUntil("follow target acquired", 200, targetAcquiredSeen)
			.assertThat("follow goal active", followGoalActive::get)
			.action("move target farther away", injectFarMove)
			.waitUntil("move closer active", 100, moveCloserActive::get)
			.action("disconnect target", injectDisconnect)
			.waitUntil("follow target lost", 100, targetLostSeen);
	}
}
