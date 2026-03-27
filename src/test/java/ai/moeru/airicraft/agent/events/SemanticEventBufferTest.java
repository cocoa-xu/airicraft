package ai.moeru.airicraft.agent.events;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SemanticEventBufferTest {
	@Test
	void querySinceFiltersAndMarksTruncationAfterRollover() {
		SemanticEventBuffer buffer = new SemanticEventBuffer(3);
		buffer.append(1L, "a", Map.of("value", 1));
		buffer.append(2L, "b", Map.of("value", 2));
		buffer.append(3L, "c", Map.of("value", 3));
		buffer.append(4L, "d", Map.of("value", 4));

		SemanticEventQueryResult result = buffer.query(1L);
		assertEquals(2L, result.oldestSeqNo());
		assertEquals(4L, result.latestSeqNo());
		assertTrue(result.truncated());
		assertEquals(3, result.events().size());
		assertEquals("b", result.events().get(0).type());
		assertEquals("d", result.events().get(2).type());
	}

	@Test
	void queryWithoutSinceReturnsCurrentBuffer() {
		SemanticEventBuffer buffer = new SemanticEventBuffer(3);
		buffer.append(10L, "session.world_loaded", Map.of());

		SemanticEventQueryResult result = buffer.query(null);
		assertEquals(1, result.events().size());
		assertEquals("session.world_loaded", result.events().get(0).type());
		assertTrue(!result.truncated());
	}
}
