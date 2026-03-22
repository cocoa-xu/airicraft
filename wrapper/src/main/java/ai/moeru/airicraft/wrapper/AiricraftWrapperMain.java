package ai.moeru.airicraft.wrapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapperSupplier;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public final class AiricraftWrapperMain {
	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
	private static final BridgeClient BRIDGE_CLIENT = new BridgeClient();

	private AiricraftWrapperMain() {
	}

	public static void main(String[] args) {
		McpJsonMapper jsonMapper = new JacksonMcpJsonMapperSupplier().get();
		StdioServerTransportProvider transportProvider = new StdioServerTransportProvider(jsonMapper);

		McpSyncServer server = McpServer.sync(transportProvider)
			.serverInfo("airicraft-wrapper", "1.0.0")
			.capabilities(McpSchema.ServerCapabilities.builder()
				.tools(true)
				.build())
			.tools(
				tool("minecraft_get_status",
					"Returns whether Minecraft is reachable and whether a world is currently loaded.",
					objectSchema(Map.of(), List.of()),
					request -> ok(BRIDGE_CLIENT.getStatus())
				),
				tool("minecraft_list_worlds",
					"Lists available singleplayer worlds that can be joined from the current client session.",
					objectSchema(Map.of(), List.of()),
					request -> withBridge(() -> ok(BRIDGE_CLIENT.listWorlds()))
				),
				tool("minecraft_join_world",
					"Starts joining a singleplayer world by worldId.",
					objectSchema(
						Map.of("worldId", stringSchema()),
						List.of("worldId")
					),
					request -> withBridge(() -> {
						Map<String, Object> argsMap = arguments(request.arguments());
						String worldId = requiredString(argsMap, "worldId");
						return ok(BRIDGE_CLIENT.joinWorld(worldId));
					})
				),
				tool("minecraft_list_servers",
					"Lists saved multiplayer servers that can be joined from the current client session.",
					objectSchema(Map.of(), List.of()),
					request -> withBridge(() -> ok(BRIDGE_CLIENT.listServers()))
				),
				tool("minecraft_join_server",
					"Starts connecting to a saved multiplayer server by serverId.",
					objectSchema(
						Map.of("serverId", stringSchema()),
						List.of("serverId")
					),
					request -> withBridge(() -> {
						Map<String, Object> argsMap = arguments(request.arguments());
						String serverId = requiredString(argsMap, "serverId");
						return ok(BRIDGE_CLIENT.joinServer(serverId));
					})
				),
				tool("minecraft_get_focus",
					"Returns structured information about the block or entity the player is currently looking at.",
					objectSchema(Map.of(), List.of()),
					request -> withBridge(() -> ok(BRIDGE_CLIENT.getFocus()))
				),
				tool("minecraft_get_world_snapshot",
					"Returns structured block data around a target position. If no position is provided, the player's current block position is used.",
					objectSchema(
						Map.of(
							"x", integerSchema(),
							"y", integerSchema(),
							"z", integerSchema(),
							"radius", integerSchema()
						),
						List.of()
					),
					request -> withBridge(() -> {
						Map<String, Object> argsMap = arguments(request.arguments());
						Integer x = optionalInt(argsMap, "x");
						Integer y = optionalInt(argsMap, "y");
						Integer z = optionalInt(argsMap, "z");
						int radius = clamp(optionalInt(argsMap, "radius"), 1, 4);
						return ok(BRIDGE_CLIENT.getWorldSnapshot(x, y, z, radius));
					})
				),
				tool("minecraft_look_at",
					"Instantly turns the player camera to face a world-space target position.",
					objectSchema(
						Map.of(
							"x", numberSchema(),
							"y", numberSchema(),
							"z", numberSchema()
						),
						List.of("x", "y", "z")
					),
					request -> withBridge(() -> {
						Map<String, Object> argsMap = arguments(request.arguments());
						return ok(BRIDGE_CLIENT.lookAt(
							requiredDouble(argsMap, "x"),
							requiredDouble(argsMap, "y"),
							requiredDouble(argsMap, "z")
						));
					})
				),
				tool("minecraft_highlight_block",
					"Highlights a block in the current client world for debugging.",
					objectSchema(
						Map.of(
							"x", integerSchema(),
							"y", integerSchema(),
							"z", integerSchema(),
							"color", stringSchema(),
							"durationSeconds", integerSchema(),
							"overlayText", stringSchema()
						),
						List.of("x", "y", "z")
					),
					request -> withBridge(() -> {
						Map<String, Object> argsMap = arguments(request.arguments());
						int x = requiredInt(argsMap, "x");
						int y = requiredInt(argsMap, "y");
						int z = requiredInt(argsMap, "z");
						String color = optionalString(argsMap, "color");
						Integer durationSeconds = optionalInt(argsMap, "durationSeconds");
						String overlayText = optionalString(argsMap, "overlayText");
						return ok(BRIDGE_CLIENT.createBlockHighlight(
							x,
							y,
							z,
							color,
							durationSeconds == null ? null : clamp(durationSeconds, 1, 86_400) * 1000L,
							overlayText
						));
					})
				),
				tool("minecraft_highlight_region",
					"Highlights an axis-aligned block region in the current client world for debugging.",
					objectSchema(
						Map.of(
							"x1", integerSchema(),
							"y1", integerSchema(),
							"z1", integerSchema(),
							"x2", integerSchema(),
							"y2", integerSchema(),
							"z2", integerSchema(),
							"color", stringSchema(),
							"durationSeconds", integerSchema(),
							"overlayText", stringSchema()
						),
						List.of("x1", "y1", "z1", "x2", "y2", "z2")
					),
					request -> withBridge(() -> {
						Map<String, Object> argsMap = arguments(request.arguments());
						Integer durationSeconds = optionalInt(argsMap, "durationSeconds");
						return ok(BRIDGE_CLIENT.createRegionHighlight(
							requiredInt(argsMap, "x1"),
							requiredInt(argsMap, "y1"),
							requiredInt(argsMap, "z1"),
							requiredInt(argsMap, "x2"),
							requiredInt(argsMap, "y2"),
							requiredInt(argsMap, "z2"),
							optionalString(argsMap, "color"),
							durationSeconds == null ? null : clamp(durationSeconds, 1, 86_400) * 1000L,
							optionalString(argsMap, "overlayText")
						));
					})
				),
				tool("minecraft_list_highlights",
					"Lists all active debug highlights.",
					objectSchema(Map.of(), List.of()),
					request -> withBridge(() -> ok(BRIDGE_CLIENT.listHighlights()))
				),
				tool("minecraft_clear_highlight",
					"Clears a single debug highlight by highlightId.",
					objectSchema(
						Map.of("highlightId", stringSchema()),
						List.of("highlightId")
					),
					request -> withBridge(() -> {
						Map<String, Object> argsMap = arguments(request.arguments());
						String highlightId = requiredString(argsMap, "highlightId");
						return ok(BRIDGE_CLIENT.clearHighlight(highlightId));
					})
				),
				tool("minecraft_clear_highlights",
					"Clears all active debug highlights.",
					objectSchema(Map.of(), List.of()),
					request -> withBridge(() -> ok(BRIDGE_CLIENT.clearHighlights()))
				)
			)
			.build();

		Runtime.getRuntime().addShutdownHook(new Thread(server::closeGracefully));
	}

	private static McpServerFeatures.SyncToolSpecification tool(
		String name,
		String description,
		McpSchema.JsonSchema inputSchema,
		FunctionWithRequest handler
	) {
		return McpServerFeatures.SyncToolSpecification.builder()
			.tool(new McpSchema.Tool(name, null, description, inputSchema, null, null, null))
			.callHandler((exchange, request) -> handler.apply(request))
			.build();
	}

	private static McpSchema.CallToolResult ok(Map<String, Object> payload) {
		return McpSchema.CallToolResult.builder()
			.structuredContent(payload)
			.addTextContent(asJson(payload))
			.build();
	}

	private static McpSchema.CallToolResult error(String code, String message) {
		Map<String, Object> payload = Map.of("error", code, "message", message);
		return McpSchema.CallToolResult.builder()
			.isError(true)
			.structuredContent(payload)
			.addTextContent(asJson(payload))
			.build();
	}

	private static McpSchema.CallToolResult withBridge(Supplier<McpSchema.CallToolResult> action) {
		try {
			return action.get();
		}
		catch (BridgeUnavailableException exception) {
			return error(exception.code(), exception.getMessage());
		}
		catch (RuntimeException exception) {
			return error("bridge_error", nonEmpty(exception.getMessage(), "Bridge request failed"));
		}
	}

	private static McpSchema.JsonSchema objectSchema(Map<String, Object> properties, List<String> required) {
		return new McpSchema.JsonSchema("object", properties, required, Boolean.FALSE, Map.of(), Map.of());
	}

	private static Map<String, Object> integerSchema() {
		return Map.of("type", "integer");
	}

	private static Map<String, Object> numberSchema() {
		return Map.of("type", "number");
	}

	private static Map<String, Object> stringSchema() {
		return Map.of("type", "string");
	}

	private static Map<String, Object> arguments(Map<String, Object> arguments) {
		return arguments == null ? Map.of() : arguments;
	}

	private static int requiredInt(Map<String, Object> arguments, String key) {
		Object value = arguments.get(key);
		if (value == null) {
			throw new BridgeUnavailableException("invalid_arguments", "Missing required argument: " + key);
		}
		return toInt(value);
	}

	private static Integer optionalInt(Map<String, Object> arguments, String key) {
		Object value = arguments.get(key);
		return value == null ? null : toInt(value);
	}

	private static double requiredDouble(Map<String, Object> arguments, String key) {
		Object value = arguments.get(key);
		if (value == null) {
			throw new BridgeUnavailableException("invalid_arguments", "Missing required argument: " + key);
		}
		return toDouble(value);
	}

	private static int clamp(Integer value, int min, int max) {
		int current = value == null ? min : value;
		return Math.max(min, Math.min(max, current));
	}

	private static String stringValue(Map<String, Object> arguments, String key, String defaultValue) {
		Object value = arguments.get(key);
		return value == null ? defaultValue : String.valueOf(value);
	}

	private static String requiredString(Map<String, Object> arguments, String key) {
		Object value = arguments.get(key);
		if (value == null) {
			throw new BridgeUnavailableException("invalid_arguments", "Missing required argument: " + key);
		}
		return String.valueOf(value);
	}

	private static String optionalString(Map<String, Object> arguments, String key) {
		Object value = arguments.get(key);
		return value == null ? null : String.valueOf(value);
	}

	private static int toInt(Object value) {
		if (value instanceof Number number) {
			return number.intValue();
		}
		return Integer.parseInt(String.valueOf(value));
	}

	private static double toDouble(Object value) {
		if (value instanceof Number number) {
			return number.doubleValue();
		}
		return Double.parseDouble(String.valueOf(value));
	}

	private static String asJson(Map<String, Object> payload) {
		try {
			return OBJECT_MAPPER.writeValueAsString(payload);
		}
		catch (JsonProcessingException exception) {
			return payload.toString();
		}
	}

	private static String nonEmpty(String value, String fallback) {
		return value == null || value.isBlank() ? fallback : value;
	}

	@FunctionalInterface
	private interface FunctionWithRequest {
		McpSchema.CallToolResult apply(McpSchema.CallToolRequest request);
	}
}
