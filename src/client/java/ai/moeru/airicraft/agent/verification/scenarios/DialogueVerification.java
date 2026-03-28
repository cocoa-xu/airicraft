package ai.moeru.airicraft.agent.verification.scenarios;

import ai.moeru.airicraft.agent.verification.ScenarioBuilder;
import ai.moeru.airicraft.agent.verification.VerificationScenario;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public final class DialogueVerification extends VerificationScenario {
	private final BooleanSupplier worldLoaded;
	private final Runnable injectNearbyPlayer;
	private final Runnable setupMockPlannerResponse;
	private final Runnable injectFollowRequest;
	private final Supplier<Boolean> dialogueResponseAvailable;
	private final Supplier<Boolean> replyTextNonEmpty;
	private final BooleanSupplier chatObserved;
	private final Supplier<Boolean> followGoalActive;

	public DialogueVerification(
		BooleanSupplier worldLoaded,
		Runnable injectNearbyPlayer,
		Runnable setupMockPlannerResponse,
		Runnable injectFollowRequest,
		Supplier<Boolean> dialogueResponseAvailable,
		Supplier<Boolean> replyTextNonEmpty,
		BooleanSupplier chatObserved,
		Supplier<Boolean> followGoalActive
	) {
		this.worldLoaded = Objects.requireNonNull(worldLoaded, "worldLoaded");
		this.injectNearbyPlayer = Objects.requireNonNull(injectNearbyPlayer, "injectNearbyPlayer");
		this.setupMockPlannerResponse = Objects.requireNonNull(setupMockPlannerResponse, "setupMockPlannerResponse");
		this.injectFollowRequest = Objects.requireNonNull(injectFollowRequest, "injectFollowRequest");
		this.dialogueResponseAvailable = Objects.requireNonNull(dialogueResponseAvailable, "dialogueResponseAvailable");
		this.replyTextNonEmpty = Objects.requireNonNull(replyTextNonEmpty, "replyTextNonEmpty");
		this.chatObserved = Objects.requireNonNull(chatObserved, "chatObserved");
		this.followGoalActive = Objects.requireNonNull(followGoalActive, "followGoalActive");
	}

	@Override
	public String name() {
		return "dialogue.basic";
	}

	@Override
	protected void define(ScenarioBuilder builder) {
		builder
			.require("in world", worldLoaded)
			.action("inject nearby player", injectNearbyPlayer)
			.action("setup mock planner response", setupMockPlannerResponse)
			.action("inject follow request", injectFollowRequest)
			.waitUntil("dialogue response available", 300, dialogueResponseAvailable::get)
			.assertThat("reply text is non-empty", replyTextNonEmpty::get)
			.waitUntil("agent sent chat", 100, chatObserved)
			.assertThat("follow goal active", followGoalActive::get);
	}
}
