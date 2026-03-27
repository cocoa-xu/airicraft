package ai.moeru.airicraft.agent.social;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrimaryInteractionResolverTest {
	@Test
	void expiresAfterConfiguredInactivityWindow() {
		PrimaryInteractionResolver resolver = new PrimaryInteractionResolver(200L);
		NearbyPlayerSnapshot alice = new NearbyPlayerSnapshot(UUID.randomUUID(), "Alice", 0.0D, 64.0D, 0.0D, false);

		resolver.onPlayerSpoke(alice, 100L);
		assertEquals("Alice", resolver.current().orElseThrow().name());

		resolver.expireInactive(299L);
		assertEquals("Alice", resolver.current().orElseThrow().name());

		resolver.expireInactive(300L);
		assertTrue(resolver.current().isEmpty());
	}

	@Test
	void clearsImmediatelyWhenCurrentPlayerLeavesNearbySet() {
		PrimaryInteractionResolver resolver = new PrimaryInteractionResolver(200L);
		UUID aliceId = UUID.randomUUID();
		NearbyPlayerSnapshot alice = new NearbyPlayerSnapshot(aliceId, "Alice", 0.0D, 64.0D, 0.0D, false);

		resolver.onPlayerSpoke(alice, 5L);
		resolver.clearIfNotNearby(aliceId, false);

		assertTrue(resolver.current().isEmpty());
	}
}
