package ai.moeru.airicraft.agent;

import ai.moeru.airicraft.Airicraft;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class AgentConfigLoader {
	private static final Gson GSON = new Gson();
	private static final String TEMPLATE_RESOURCE = "/config/airicraft/agent.json.example";
	private static final String TEMPLATE_FILENAME = "agent.json.example";
	private static final String CONFIG_FILENAME = "agent.json";

	private AgentConfigLoader() {
	}

	public static AgentConfig load() {
		AgentConfig defaults = AgentConfig.defaults();
		Path configDir = FabricLoader.getInstance().getConfigDir().resolve("airicraft");
		Path templatePath = configDir.resolve(TEMPLATE_FILENAME);
		Path configPath = configDir.resolve(CONFIG_FILENAME);

		try {
			Files.createDirectories(configDir);
			ensureFile(templatePath);
			if (Files.notExists(configPath)) {
				Files.copy(templatePath, configPath);
			}

			try (Reader fileReader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
				JsonObject root = parseLenient(fileReader);
				AgentConfig.LlmConfig llm = new AgentConfig.LlmConfig(
					readString(root, "providerBaseUrl", defaults.llm().providerBaseUrl()),
					readString(root, "apiKey", defaults.llm().apiKey()),
					readString(root, "model", defaults.llm().model()),
					readInt(root, "requestTimeoutMillis", defaults.llm().requestTimeoutMillis()),
					readInt(root, "maxRecentConversationTurns", defaults.llm().maxRecentConversationTurns()),
					readBoolean(root, "enableProactiveSocialMode", defaults.llm().enableProactiveSocialMode())
				);
				return new AgentConfig(defaults.verificationEnabled(), defaults.verificationAutoRunAll(), llm);
			}
		}
		catch (IOException | JsonParseException exception) {
			Airicraft.LOGGER.warn("Failed to load Airicraft agent config; using defaults", exception);
			return defaults;
		}
	}

	private static void ensureFile(Path path) throws IOException {
		if (Files.exists(path)) {
			return;
		}

		try (InputStream stream = AgentConfigLoader.class.getResourceAsStream(TEMPLATE_RESOURCE)) {
			if (stream == null) {
				throw new IOException("Missing embedded agent config template: " + TEMPLATE_RESOURCE);
			}
			Files.copy(stream, path);
		}
	}

	private static JsonObject parseLenient(Reader reader) {
		JsonReader jsonReader = new JsonReader(reader);
		jsonReader.setLenient(true);
		return JsonParser.parseReader(jsonReader).getAsJsonObject();
	}

	private static String readString(JsonObject root, String fieldName, String fallback) {
		if (root == null || !root.has(fieldName) || root.get(fieldName).isJsonNull()) {
			return fallback;
		}
		return root.get(fieldName).getAsString();
	}

	private static int readInt(JsonObject root, String fieldName, int fallback) {
		if (root == null || !root.has(fieldName) || root.get(fieldName).isJsonNull()) {
			return fallback;
		}
		return root.get(fieldName).getAsInt();
	}

	private static boolean readBoolean(JsonObject root, String fieldName, boolean fallback) {
		if (root == null || !root.has(fieldName) || root.get(fieldName).isJsonNull()) {
			return fallback;
		}
		return root.get(fieldName).getAsBoolean();
	}
}
