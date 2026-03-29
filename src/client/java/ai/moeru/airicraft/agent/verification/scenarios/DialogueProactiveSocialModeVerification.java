package ai.moeru.airicraft.agent.verification.scenarios;

import ai.moeru.airicraft.agent.verification.ScenarioBuilder;
import ai.moeru.airicraft.agent.verification.VerificationScenario;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

public final class DialogueProactiveSocialModeVerification extends VerificationScenario {
	private final BooleanSupplier worldLoaded;
	private final Runnable injectNearbyPlayer;
	private final Consumer<Boolean> setProactiveModeOverride;
	private final LongSupplier lastDialogueResponseTick;
	private final LongSupplier currentTick;
	private final Runnable injectNonAddressedChat;
	private final Runnable setupMockResponse;

	private long baselineDialogueTick;
	private long settleUntilTick;

	public DialogueProactiveSocialModeVerification(
		BooleanSupplier worldLoaded,
		Runnable injectNearbyPlayer,
		Consumer<Boolean> setProactiveModeOverride,
		LongSupplier lastDialogueResponseTick,
		LongSupplier currentTick,
		Runnable injectNonAddressedChat,
		Runnable setupMockResponse
	) {
		this.worldLoaded = Objects.requireNonNull(worldLoaded, "worldLoaded");
		this.injectNearbyPlayer = Objects.requireNonNull(injectNearbyPlayer, "injectNearbyPlayer");
		this.setProactiveModeOverride = Objects.requireNonNull(setProactiveModeOverride, "setProactiveModeOverride");
		this.lastDialogueResponseTick = Objects.requireNonNull(lastDialogueResponseTick, "lastDialogueResponseTick");
		this.currentTick = Objects.requireNonNull(currentTick, "currentTick");
		this.injectNonAddressedChat = Objects.requireNonNull(injectNonAddressedChat, "injectNonAddressedChat");
		this.setupMockResponse = Objects.requireNonNull(setupMockResponse, "setupMockResponse");
	}

	@Override
	public String name() {
		return "dialogue.proactive_social_mode";
	}

	@Override
	protected void define(ScenarioBuilder builder) {
		builder
			.require("in world", worldLoaded)
			.action("inject nearby player", injectNearbyPlayer)
			.action("disable proactive social mode", () -> setProactiveModeOverride.accept(Boolean.FALSE))
			.action("capture response baseline", () -> baselineDialogueTick = lastDialogueResponseTick.getAsLong())
			.action("inject non-addressed chat with proactive mode off", injectNonAddressedChat)
			.action("wait for planner quiet period", () -> settleUntilTick = currentTick.getAsLong() + 20L)
			.waitUntil("quiet period elapsed", 40, () -> currentTick.getAsLong() >= settleUntilTick)
			.assertThat("non-addressed chat does not reach planner while off", () -> lastDialogueResponseTick.getAsLong() == baselineDialogueTick)
			.action("enable proactive social mode", () -> setProactiveModeOverride.accept(Boolean.TRUE))
			.action("setup mock response", setupMockResponse)
			.action("inject non-addressed chat with proactive mode on", injectNonAddressedChat)
			.waitUntil("non-addressed chat reaches planner while on", 300, () -> lastDialogueResponseTick.getAsLong() > baselineDialogueTick)
			.action("clear proactive mode override", () -> setProactiveModeOverride.accept(null));
	}
}
