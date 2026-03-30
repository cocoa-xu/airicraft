package ai.moeru.airicraft;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiricraftConfigLoaderTest {
	@Test
	void fromMapReadsChatRuntimeSettings() {
		AiricraftConfig defaults = AiricraftConfig.defaults();

		AiricraftConfig parsed = AiricraftConfigLoader.fromMap(Map.of(
			"socialChatMaxDistanceBlocks", 96,
			"readSystemChatMessages", false,
			"enableProactiveSocialMode", true
		), defaults);

		assertEquals(96, parsed.socialChatMaxDistanceBlocks());
		assertFalse(parsed.readSystemChatMessages());
		assertTrue(parsed.enableProactiveSocialMode());
	}
}
