package ai.moeru.airicraft.wrapper;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiricraftCliMainTest {
	@Test
	void statusRendersDeterministicText() {
		TestTransport transport = new TestTransport();
		transport.statusPayload = linkedMap(
			"available", false,
			"bridgeAvailable", false,
			"worldLoaded", false,
			"sessionState", "minecraft_unavailable",
			"state", "minecraft_unavailable",
			"message", "Minecraft bridge is not active"
		);

		CliResult result = execute(transport, "status");

		assertEquals(0, result.exitCode());
		assertTrue(result.output().startsWith(
			"status: ok\n" +
				"command: status\n" +
				"available: false\n"
		));
		assertTrue(result.output().contains("state: minecraft_unavailable\n"));
	}

	@Test
	void worldsListOmitsVerboseFieldsByDefault() {
		TestTransport transport = new TestTransport();
		transport.worldsPayload = linkedMap(
			"available", true,
			"sessionState", "out_of_world",
			"worlds", List.of(linkedMap(
				"worldId", "survival-12345678",
				"name", "survival",
				"displayName", "Survival",
				"lastPlayed", 123L,
				"gameMode", "survival",
				"selectable", true,
				"immediatelyLoadable", true,
				"locked", false,
				"unavailable", false,
				"experimental", false,
				"details", "A long details string",
				"version", "1.21.11"
			))
		);

		CliResult compact = execute(transport, "worlds", "list");
		CliResult verbose = execute(transport, "worlds", "list", "--verbose");

		assertEquals(0, compact.exitCode());
		assertTrue(compact.output().contains("worldCount: 1\n"));
		assertFalse(compact.output().contains("details:"));
		assertTrue(verbose.output().contains("details: A long details string\n"));
		assertTrue(verbose.output().contains("version: 1.21.11\n"));
	}

	@Test
	void missingRequiredArgumentReturnsUsageError() {
		CliResult result = execute(new TestTransport(), "worlds", "join");

		assertEquals(2, result.exitCode());
		assertTrue(result.output().contains("status: error\n"));
		assertTrue(result.output().contains("command: worlds join\n"));
		assertTrue(result.output().contains("error_code: invalid_arguments\n"));
		assertTrue(result.output().contains("Missing required option: '--world-id"));
	}

	@Test
	void bridgeErrorsMapToExitCodes() {
		TestTransport transport = new TestTransport();
		transport.worldsJoinFailure = new BridgeUnavailableException("already_in_world", "A world is already loaded");
		transport.serversListFailure = new BridgeUnavailableException("minecraft_unavailable", "Minecraft bridge is not active");

		CliResult domainFailure = execute(transport, "worlds", "join", "--world-id", "survival-12345678");
		CliResult transportFailure = execute(transport, "servers", "list");

		assertEquals(4, domainFailure.exitCode());
		assertTrue(domainFailure.output().contains("error_code: already_in_world\n"));
		assertEquals(3, transportFailure.exitCode());
		assertTrue(transportFailure.output().contains("error_code: minecraft_unavailable\n"));
	}

	@Test
	void helpCommandShowsScopedUsage() {
		CliResult result = execute(new TestTransport(), "help", "highlights", "block");

		assertEquals(0, result.exitCode());
		assertTrue(result.output().contains("Usage: airicraft highlights block"));
		assertTrue(result.output().contains("--x"));
		assertTrue(result.output().contains("--duration-seconds"));
	}

	private static CliResult execute(MinecraftTransport transport, String... args) {
		StringWriter writer = new StringWriter();
		CommandLine commandLine = AiricraftCliMain.createCommandLine(transport, new PrintWriter(writer, true));
		int exitCode = commandLine.execute(args);
		return new CliResult(exitCode, writer.toString().replace("\r\n", "\n"));
	}

	private static LinkedHashMap<String, Object> linkedMap(Object... values) {
		LinkedHashMap<String, Object> map = new LinkedHashMap<>();
		for (int index = 0; index < values.length; index += 2) {
			map.put(String.valueOf(values[index]), values[index + 1]);
		}
		return map;
	}

	private record CliResult(int exitCode, String output) {
	}

	private static final class TestTransport implements MinecraftTransport {
		private Map<String, Object> statusPayload = Map.of();
		private Map<String, Object> worldsPayload = Map.of("worlds", List.of());
		private Map<String, Object> serversPayload = Map.of("servers", List.of());
		private Map<String, Object> focusPayload = Map.of();
		private Map<String, Object> snapshotPayload = Map.of();
		private Map<String, Object> blockHighlightPayload = Map.of("highlightId", "highlight-1");
		private Map<String, Object> regionHighlightPayload = Map.of("highlightId", "highlight-2");
		private Map<String, Object> highlightsPayload = Map.of("highlights", List.of());
		private Map<String, Object> clearHighlightPayload = Map.of("cleared", true);
		private Map<String, Object> clearHighlightsPayload = Map.of("cleared", true, "clearedCount", 0);
		private Map<String, Object> worldsJoinPayload = Map.of("started", true);
		private Map<String, Object> serversJoinPayload = Map.of("started", true);
		private Map<String, Object> lookAtPayload = Map.of("started", true);

		private RuntimeException worldsJoinFailure;
		private RuntimeException serversListFailure;

		@Override
		public Map<String, Object> getStatus() {
			return statusPayload;
		}

		@Override
		public Map<String, Object> getFocus() {
			return focusPayload;
		}

		@Override
		public Map<String, Object> getWorldSnapshot(Integer x, Integer y, Integer z, int radius) {
			return snapshotPayload;
		}

		@Override
		public Map<String, Object> listWorlds() {
			return worldsPayload;
		}

		@Override
		public Map<String, Object> joinWorld(String worldId) {
			if (worldsJoinFailure != null) {
				throw worldsJoinFailure;
			}
			return worldsJoinPayload;
		}

		@Override
		public Map<String, Object> listServers() {
			if (serversListFailure != null) {
				throw serversListFailure;
			}
			return serversPayload;
		}

		@Override
		public Map<String, Object> joinServer(String serverId) {
			return serversJoinPayload;
		}

		@Override
		public Map<String, Object> lookAt(double x, double y, double z) {
			return lookAtPayload;
		}

		@Override
		public Map<String, Object> createBlockHighlight(int x, int y, int z, String color, Long durationMs, String overlayText) {
			return blockHighlightPayload;
		}

		@Override
		public Map<String, Object> createRegionHighlight(int x1, int y1, int z1, int x2, int y2, int z2, String color, Long durationMs, String overlayText) {
			return regionHighlightPayload;
		}

		@Override
		public Map<String, Object> listHighlights() {
			return highlightsPayload;
		}

		@Override
		public Map<String, Object> clearHighlight(String highlightId) {
			return clearHighlightPayload;
		}

		@Override
		public Map<String, Object> clearHighlights() {
			return clearHighlightsPayload;
		}
	}
}
