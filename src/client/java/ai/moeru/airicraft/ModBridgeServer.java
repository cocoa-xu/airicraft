package ai.moeru.airicraft;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.registry.Registries;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public final class ModBridgeServer {
	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
	private static final SecureRandom RANDOM = new SecureRandom();

	private final HighlightManager highlightManager;

	private volatile HttpServer server;
	private volatile String token;

	public ModBridgeServer(HighlightManager highlightManager) {
		this.highlightManager = highlightManager;
	}

	public synchronized void start() {
		if (server != null) {
			return;
		}

		try {
			var httpServer = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
			httpServer.setExecutor(Executors.newCachedThreadPool());
			token = generateToken();
			httpServer.createContext("/v1/status", exchange -> handleJson(exchange, this::createStatusResponse));
			httpServer.createContext("/v1/focus", exchange -> handleJson(exchange, this::createFocusResponse));
			httpServer.createContext("/v1/world-snapshot", exchange -> handleJson(exchange, () -> createWorldSnapshotResponse(exchange)));
			httpServer.createContext("/v1/highlights", this::handleHighlights);
			httpServer.start();

			server = httpServer;

			var state = new BridgeSessionState(httpServer.getAddress().getPort(), token, Instant.now().toEpochMilli());
			BridgeDiscoveryFile.write(state);
			Airicraft.LOGGER.info("Airicraft bridge started on port {}", state.port());
		}
		catch (IOException exception) {
			Airicraft.LOGGER.error("Failed to start Airicraft bridge", exception);
			stop();
		}
	}

	public synchronized void stop() {
		var currentServer = server;
		server = null;
		token = null;

		if (currentServer != null) {
			currentServer.stop(0);
			Airicraft.LOGGER.info("Airicraft bridge stopped");
		}

		BridgeDiscoveryFile.deleteIfPresent();
	}

	private void handleHighlights(HttpExchange exchange) throws IOException {
		if (!authorize(exchange)) {
			writeJson(exchange, 401, Map.of("error", "unauthorized", "message", "Invalid bridge token"));
			return;
		}

		if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
			HighlightRequest request;
			try (var reader = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8)) {
				request = GSON.fromJson(reader, HighlightRequest.class);
			}
			catch (JsonSyntaxException exception) {
				writeJson(exchange, 400, Map.of("error", "invalid_json", "message", "Malformed highlight request"));
				return;
			}

			if (request == null) {
				writeJson(exchange, 400, Map.of("error", "invalid_request", "message", "Missing highlight payload"));
				return;
			}

			var highlightId = onClientThread(() -> {
				var client = getClient();
				ensureWorldLoaded(client);
				return highlightManager.add(new BlockPos(request.x(), request.y(), request.z()));
			});

			writeJson(exchange, 200, Map.of("highlightId", highlightId));
			return;
		}

		if ("DELETE".equalsIgnoreCase(exchange.getRequestMethod())) {
			onClientThread(() -> {
				var client = getClient();
				ensureWorldLoaded(client);
				highlightManager.clear();
				return Map.of("cleared", true);
			});
			writeJson(exchange, 200, Map.of("cleared", true));
			return;
		}

		writeJson(exchange, 405, Map.of("error", "method_not_allowed"));
	}

	private void handleJson(HttpExchange exchange, Supplier<Object> supplier) throws IOException {
		if (!authorize(exchange)) {
			writeJson(exchange, 401, Map.of("error", "unauthorized", "message", "Invalid bridge token"));
			return;
		}

		if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
			writeJson(exchange, 405, Map.of("error", "method_not_allowed"));
			return;
		}

		try {
			Object response = supplier.get();
			writeJson(exchange, 200, response);
		}
		catch (BridgeUnavailableException exception) {
			writeJson(exchange, 503, Map.of("error", exception.code(), "message", exception.getMessage()));
		}
		catch (Exception exception) {
			Airicraft.LOGGER.warn("Bridge request failed", exception);
			writeJson(exchange, 500, Map.of("error", "internal_error", "message", exception.getMessage()));
		}
	}

	private Object createStatusResponse() {
		return onClientThread(() -> createStatusSnapshot(getClient()));
	}

	private Object createFocusResponse() {
		return onClientThread(() -> {
			var client = getClient();
			ensureWorldLoaded(client);
			return Map.of("available", true, "focus", describeFocus(client));
		});
	}

	private Object createWorldSnapshotResponse(HttpExchange exchange) {
		int x = getIntQuery(exchange, "x", Integer.MIN_VALUE);
		int y = getIntQuery(exchange, "y", Integer.MIN_VALUE);
		int z = getIntQuery(exchange, "z", Integer.MIN_VALUE);
		int radius = Math.max(0, Math.min(getIntQuery(exchange, "radius", 1), 4));

		return onClientThread(() -> {
			var client = getClient();
			ensureWorldLoaded(client);

			BlockPos center;
			if (x == Integer.MIN_VALUE || y == Integer.MIN_VALUE || z == Integer.MIN_VALUE) {
				center = Objects.requireNonNull(client.player).getBlockPos();
			}
			else {
				center = new BlockPos(x, y, z);
			}

			return Map.of(
				"available", true,
				"center", blockPos(center),
				"radius", radius,
				"blocks", collectBlocks(client.world, center, radius)
			);
		});
	}

	private Map<String, Object> createStatusSnapshot(MinecraftClient client) {
		var world = client.world;
		var player = client.player;
		boolean worldLoaded = world != null && player != null;

		Map<String, Object> response = new LinkedHashMap<>();
		response.put("available", true);
		response.put("bridgeAvailable", true);
		response.put("worldLoaded", worldLoaded);

		if (!worldLoaded) {
			response.put("state", "world_not_loaded");
			return response;
		}

		response.put("state", "ready");
		response.put("dimension", world.getRegistryKey().getValue().toString());
		response.put("player", Map.of(
			"name", player.getName().getString(),
			"x", player.getX(),
			"y", player.getY(),
			"z", player.getZ(),
			"blockPos", blockPos(player.getBlockPos())
		));
		response.put("focus", describeFocus(client));
		return response;
	}

	private List<Map<String, Object>> collectBlocks(World world, BlockPos center, int radius) {
		List<Map<String, Object>> blocks = new ArrayList<>();
		for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
			for (int y = center.getY() - radius; y <= center.getY() + radius; y++) {
				for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
					var pos = new BlockPos(x, y, z);
					BlockState blockState = world.getBlockState(pos);
					blocks.add(Map.of(
						"pos", blockPos(pos),
						"block", Registries.BLOCK.getId(blockState.getBlock()).toString(),
						"state", blockState.toString()
					));
				}
			}
		}
		return blocks;
	}

	private Map<String, Object> describeFocus(MinecraftClient client) {
		HitResult hitResult = client.crosshairTarget;
		if (hitResult == null) {
			return Map.of("type", "none");
		}

		if (hitResult instanceof BlockHitResult blockHit) {
			BlockPos pos = blockHit.getBlockPos();
			BlockState state = Objects.requireNonNull(client.world).getBlockState(pos);
			return Map.of(
				"type", "block",
				"pos", blockPos(pos),
				"block", Registries.BLOCK.getId(state.getBlock()).toString(),
				"side", blockHit.getSide().asString()
			);
		}

		if (hitResult instanceof EntityHitResult entityHit) {
			Entity entity = entityHit.getEntity();
			return Map.of(
				"type", "entity",
				"entityType", Registries.ENTITY_TYPE.getId(entity.getType()).toString(),
				"name", entity.getName().getString(),
				"id", entity.getId(),
				"pos", Map.of(
					"x", entity.getX(),
					"y", entity.getY(),
					"z", entity.getZ()
				)
			);
		}

		return Map.of("type", hitResult.getType().name().toLowerCase());
	}

	private static Map<String, Integer> blockPos(BlockPos pos) {
		return Map.of("x", pos.getX(), "y", pos.getY(), "z", pos.getZ());
	}

	private boolean authorize(HttpExchange exchange) {
		Headers headers = exchange.getRequestHeaders();
		var authorization = headers.getFirst("Authorization");
		return authorization != null && authorization.equals("Bearer " + token);
	}

	private <T> T onClientThread(Supplier<T> supplier) {
		var client = getClient();
		CompletableFuture<T> future = new CompletableFuture<>();
		client.execute(() -> {
			try {
				future.complete(supplier.get());
			}
			catch (Throwable throwable) {
				future.completeExceptionally(throwable);
			}
		});

		try {
			return future.get(5, TimeUnit.SECONDS);
		}
		catch (ExecutionException exception) {
			if (exception.getCause() instanceof BridgeUnavailableException bridgeUnavailableException) {
				throw bridgeUnavailableException;
			}

			throw new IllegalStateException("Bridge request failed on Minecraft client thread", exception.getCause());
		}
		catch (Exception exception) {
			throw new IllegalStateException("Timed out waiting for Minecraft client thread", exception);
		}
	}

	private MinecraftClient getClient() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null) {
			throw new BridgeUnavailableException("minecraft_unavailable", "Minecraft client is not initialized");
		}
		return client;
	}

	private void ensureWorldLoaded(MinecraftClient client) {
		if (client.world == null || client.player == null) {
			throw new BridgeUnavailableException("world_not_loaded", "No world is currently loaded");
		}
	}

	private static int getIntQuery(HttpExchange exchange, String key, int defaultValue) {
		var rawQuery = exchange.getRequestURI().getRawQuery();
		if (rawQuery == null || rawQuery.isBlank()) {
			return defaultValue;
		}

		for (String part : rawQuery.split("&")) {
			var split = part.split("=", 2);
			if (split.length == 2 && split[0].equals(key)) {
				try {
					return Integer.parseInt(split[1]);
				}
				catch (NumberFormatException ignored) {
					return defaultValue;
				}
			}
		}

		return defaultValue;
	}

	private static void writeJson(HttpExchange exchange, int statusCode, Object body) throws IOException {
		byte[] response = GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().set("Content-Type", "application/json");
		exchange.sendResponseHeaders(statusCode, response.length);

		try (OutputStream outputStream = exchange.getResponseBody()) {
			outputStream.write(response);
		}
	}

	private static String generateToken() {
		byte[] bytes = new byte[24];
		RANDOM.nextBytes(bytes);
		StringBuilder builder = new StringBuilder(bytes.length * 2);
		for (byte current : bytes) {
			builder.append(String.format("%02x", current));
		}
		return builder.toString();
	}

	private record HighlightRequest(
		int x,
		int y,
		int z,
		String color,
		long durationMs
	) {
	}

	private static final class BridgeUnavailableException extends RuntimeException {
		private final String code;

		private BridgeUnavailableException(String code, String message) {
			super(message);
			this.code = code;
		}

		private String code() {
			return code;
		}
	}
}
