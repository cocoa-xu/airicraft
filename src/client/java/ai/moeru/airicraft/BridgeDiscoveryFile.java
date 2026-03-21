package ai.moeru.airicraft;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class BridgeDiscoveryFile {
	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
	private static final Path BRIDGE_FILE = Paths.get(System.getProperty("user.home"), ".airicraft", "bridge-state.json");

	private BridgeDiscoveryFile() {
	}

	public static Path path() {
		return BRIDGE_FILE;
	}

	public static void write(BridgeSessionState state) throws IOException {
		Files.createDirectories(BRIDGE_FILE.getParent());

		try (Writer writer = Files.newBufferedWriter(BRIDGE_FILE)) {
			GSON.toJson(state, writer);
		}
	}

	public static BridgeSessionState read() throws IOException {
		try (Reader reader = Files.newBufferedReader(BRIDGE_FILE)) {
			return GSON.fromJson(reader, BridgeSessionState.class);
		}
	}

	public static void deleteIfPresent() {
		try {
			Files.deleteIfExists(BRIDGE_FILE);
		}
		catch (IOException exception) {
			Airicraft.LOGGER.warn("Failed to delete bridge discovery file at {}", BRIDGE_FILE, exception);
		}
	}
}
