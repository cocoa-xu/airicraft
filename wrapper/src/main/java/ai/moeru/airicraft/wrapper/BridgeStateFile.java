package ai.moeru.airicraft.wrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class BridgeStateFile {
	private static final Path BRIDGE_FILE = Paths.get(System.getProperty("user.home"), ".airicraft", "bridge-state.json");
	private static final Pattern PORT_PATTERN = Pattern.compile("\"port\"\\s*:\\s*(\\d+)");
	private static final Pattern TOKEN_PATTERN = Pattern.compile("\"token\"\\s*:\\s*\"([^\"]+)\"");
	private static final Pattern STARTED_PATTERN = Pattern.compile("\"startedAtEpochMillis\"\\s*:\\s*(\\d+)");

	private BridgeStateFile() {
	}

	static Optional<BridgeState> read() {
		if (!Files.exists(BRIDGE_FILE)) {
			return Optional.empty();
		}

		try {
			String raw = Files.readString(BRIDGE_FILE, StandardCharsets.UTF_8);
			Matcher portMatcher = PORT_PATTERN.matcher(raw);
			Matcher tokenMatcher = TOKEN_PATTERN.matcher(raw);
			Matcher startedMatcher = STARTED_PATTERN.matcher(raw);
			if (!portMatcher.find() || !tokenMatcher.find() || !startedMatcher.find()) {
				return Optional.empty();
			}

			return Optional.of(new BridgeState(
				Integer.parseInt(portMatcher.group(1)),
				tokenMatcher.group(1),
				Long.parseLong(startedMatcher.group(1))
			));
		}
		catch (IOException | RuntimeException exception) {
			return Optional.empty();
		}
	}

	record BridgeState(int port, String token, long startedAtEpochMillis) {
	}
}
