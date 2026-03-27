package ai.moeru.airicraft.agent.social;

import ai.moeru.airicraft.agent.events.SemanticEventBuffer;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class ChatIngestService {
	public void ingest(
		String senderName,
		String plainTextMessage,
		long tick,
		NearbyPlayerTracker nearbyPlayerTracker,
		PrimaryInteractionResolver primaryInteractionResolver,
		SemanticEventBuffer eventBuffer
	) {
		Objects.requireNonNull(senderName, "senderName");
		Objects.requireNonNull(plainTextMessage, "plainTextMessage");
		Objects.requireNonNull(nearbyPlayerTracker, "nearbyPlayerTracker");
		Objects.requireNonNull(primaryInteractionResolver, "primaryInteractionResolver");
		Objects.requireNonNull(eventBuffer, "eventBuffer");

		Optional<NearbyPlayerSnapshot> nearbyPlayer = nearbyPlayerTracker.findByName(senderName);
		if (nearbyPlayer.isEmpty()) {
			return;
		}

		String normalizedMessage = normalize(plainTextMessage);
		eventBuffer.append(tick, "social.player_spoke", Map.of(
			"player", senderName,
			"message", plainTextMessage,
			"normalizedMessage", normalizedMessage
		));
		primaryInteractionResolver.onPlayerSpoke(nearbyPlayer.get(), tick);

		if (isAddressedToAgent(plainTextMessage)) {
			eventBuffer.append(tick, "social.player_addressed_agent", Map.of(
				"player", senderName,
				"message", plainTextMessage,
				"normalizedMessage", normalizedMessage
			));
		}
	}

	public void injectMessage(
		String senderName,
		String plainTextMessage,
		long tick,
		NearbyPlayerTracker nearbyPlayerTracker,
		PrimaryInteractionResolver primaryInteractionResolver,
		SemanticEventBuffer eventBuffer
	) {
		ingest(senderName, plainTextMessage, tick, nearbyPlayerTracker, primaryInteractionResolver, eventBuffer);
	}

	public static boolean isAddressedToAgent(String plainTextMessage) {
		String normalizedMessage = normalize(plainTextMessage);
		return normalizedMessage.regionMatches(true, 0, "@agent", 0, "@agent".length());
	}

	public static String normalize(String plainTextMessage) {
		return plainTextMessage.stripLeading();
	}
}
