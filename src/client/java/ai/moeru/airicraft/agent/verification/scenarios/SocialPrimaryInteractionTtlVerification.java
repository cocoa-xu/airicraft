package ai.moeru.airicraft.agent.verification.scenarios;

import ai.moeru.airicraft.agent.verification.ScenarioBuilder;
import ai.moeru.airicraft.agent.verification.VerificationScenario;

import java.util.Objects;
import java.util.function.BooleanSupplier;

public final class SocialPrimaryInteractionTtlVerification extends VerificationScenario {
	private final BooleanSupplier worldLoaded;
	private final Runnable injectAliceNearby;
	private final Runnable injectAliceMessage;
	private final BooleanSupplier alicePrimary;
	private final BooleanSupplier primaryCleared;
	private final Runnable injectBobNearby;
	private final Runnable injectBobMessage;
	private final BooleanSupplier bobPrimary;

	public SocialPrimaryInteractionTtlVerification(
		BooleanSupplier worldLoaded,
		Runnable injectAliceNearby,
		Runnable injectAliceMessage,
		BooleanSupplier alicePrimary,
		BooleanSupplier primaryCleared,
		Runnable injectBobNearby,
		Runnable injectBobMessage,
		BooleanSupplier bobPrimary
	) {
		this.worldLoaded = Objects.requireNonNull(worldLoaded, "worldLoaded");
		this.injectAliceNearby = Objects.requireNonNull(injectAliceNearby, "injectAliceNearby");
		this.injectAliceMessage = Objects.requireNonNull(injectAliceMessage, "injectAliceMessage");
		this.alicePrimary = Objects.requireNonNull(alicePrimary, "alicePrimary");
		this.primaryCleared = Objects.requireNonNull(primaryCleared, "primaryCleared");
		this.injectBobNearby = Objects.requireNonNull(injectBobNearby, "injectBobNearby");
		this.injectBobMessage = Objects.requireNonNull(injectBobMessage, "injectBobMessage");
		this.bobPrimary = Objects.requireNonNull(bobPrimary, "bobPrimary");
	}

	@Override
	public String name() {
		return "social.primary_interaction_ttl";
	}

	@Override
	protected void define(ScenarioBuilder builder) {
		builder
			.require("in world", worldLoaded)
			.action("inject Alice nearby", injectAliceNearby)
			.action("inject Alice message", injectAliceMessage)
			.assertThat("Alice becomes primary interaction player", alicePrimary)
			.waitUntil("primary interaction player expires", 260, primaryCleared)
			.action("inject Bob nearby", injectBobNearby)
			.action("inject Bob message", injectBobMessage)
			.assertThat("Bob becomes primary interaction player", bobPrimary);
	}
}
