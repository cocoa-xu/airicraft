package ai.moeru.airicraft.agent.social;

import ai.moeru.airicraft.agent.events.SemanticEventBuffer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatIngestServiceTest {
	@Test
	void ingestRecordsPlayerChatEvenWhenSenderIsNotTrackedNearby() {
		ChatIngestService service = new ChatIngestService();
		NearbyPlayerTracker nearbyPlayerTracker = new NearbyPlayerTracker();
		PrimaryInteractionResolver primaryInteractionResolver = new PrimaryInteractionResolver(200L);
		SemanticEventBuffer eventBuffer = new SemanticEventBuffer(8);

		service.ingest("FarAwayAlice", "@agent can you hear me?", 10L, nearbyPlayerTracker, primaryInteractionResolver, eventBuffer);

		assertTrue(eventBuffer.containsTypeForPlayer("social.player_spoke", "FarAwayAlice"));
		assertTrue(eventBuffer.containsTypeForPlayer("social.player_addressed_agent", "FarAwayAlice"));
		assertTrue(primaryInteractionResolver.current().isEmpty());
	}

	@Test
	void ingestSystemMessageAppendsDedicatedEvent() {
		ChatIngestService service = new ChatIngestService();
		SemanticEventBuffer eventBuffer = new SemanticEventBuffer(8);

		service.ingestSystemMessage("Player fell from a high place", 20L, eventBuffer);

		assertTrue(eventBuffer.containsType("social.system_message"));
	}

	@Test
	void ingestSystemMessageParsesJoinAndLeaveEvents() {
		ChatIngestService service = new ChatIngestService();
		SemanticEventBuffer eventBuffer = new SemanticEventBuffer(8);

		service.ingestSystemMessage("magpie joined the game", 21L, eventBuffer);
		service.ingestSystemMessage("magpie left the game", 22L, eventBuffer);

		assertTrue(eventBuffer.containsType("social.system_message"));
	}
}
