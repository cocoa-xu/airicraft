package ai.moeru.airicraft.agent.verification.scenarios;

import ai.moeru.airicraft.agent.verification.ScenarioBuilder;
import ai.moeru.airicraft.agent.verification.VerificationScenario;

import java.util.Objects;
import java.util.function.BooleanSupplier;

public final class SocialChatIngestVerification extends VerificationScenario {
	private final BooleanSupplier worldLoaded;
	private final Runnable injectNearbyPlayer;
	private final Runnable injectGreeting;
	private final Runnable injectAddressedMessage;
	private final BooleanSupplier joinedEventSeen;
	private final BooleanSupplier spokeEventSeen;
	private final BooleanSupplier addressedEventSeen;
	private final BooleanSupplier primaryInteractionPlayerSet;

	public SocialChatIngestVerification(
		BooleanSupplier worldLoaded,
		Runnable injectNearbyPlayer,
		Runnable injectGreeting,
		Runnable injectAddressedMessage,
		BooleanSupplier joinedEventSeen,
		BooleanSupplier spokeEventSeen,
		BooleanSupplier addressedEventSeen,
		BooleanSupplier primaryInteractionPlayerSet
	) {
		this.worldLoaded = Objects.requireNonNull(worldLoaded, "worldLoaded");
		this.injectNearbyPlayer = Objects.requireNonNull(injectNearbyPlayer, "injectNearbyPlayer");
		this.injectGreeting = Objects.requireNonNull(injectGreeting, "injectGreeting");
		this.injectAddressedMessage = Objects.requireNonNull(injectAddressedMessage, "injectAddressedMessage");
		this.joinedEventSeen = Objects.requireNonNull(joinedEventSeen, "joinedEventSeen");
		this.spokeEventSeen = Objects.requireNonNull(spokeEventSeen, "spokeEventSeen");
		this.addressedEventSeen = Objects.requireNonNull(addressedEventSeen, "addressedEventSeen");
		this.primaryInteractionPlayerSet = Objects.requireNonNull(primaryInteractionPlayerSet, "primaryInteractionPlayerSet");
	}

	@Override
	public String name() {
		return "social.chat_ingest";
	}

	@Override
	protected void define(ScenarioBuilder builder) {
		builder
			.require("in world", worldLoaded)
			.action("inject nearby player", injectNearbyPlayer)
			.assertThat("nearby player event seen", joinedEventSeen)
			.action("inject greeting", injectGreeting)
			.assertThat("player spoke event seen", spokeEventSeen)
			.action("inject addressed message", injectAddressedMessage)
			.assertThat("player addressed agent event seen", addressedEventSeen)
			.assertThat("primary interaction player set", primaryInteractionPlayerSet);
	}
}
