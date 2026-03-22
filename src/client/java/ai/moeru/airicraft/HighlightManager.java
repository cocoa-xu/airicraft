package ai.moeru.airicraft;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.debug.GameTestDebugRenderer;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Map;

public final class HighlightManager {
	private static final Field MARKERS_FIELD = findMarkersField();
	private static final Constructor<?> MARKER_CONSTRUCTOR = findMarkerConstructor();

	public String add(BlockPos pos, int colorArgb, int durationMs) {
		var client = MinecraftClient.getInstance();
		if (client.worldRenderer == null) {
			throw new IllegalStateException("World renderer is not initialized");
		}

		addColoredMarker(client.worldRenderer.gameTestDebugRenderer, pos, colorArgb, durationMs);
		return pos.getX() + "," + pos.getY() + "," + pos.getZ();
	}

	public void clear() {
		var client = MinecraftClient.getInstance();
		if (client.worldRenderer != null) {
			client.worldRenderer.gameTestDebugRenderer.clear();
		}
	}

	@SuppressWarnings("unchecked")
	private static void addColoredMarker(GameTestDebugRenderer renderer, BlockPos pos, int colorArgb, int durationMs) {
		try {
			renderer.addMarker(pos, pos);
			Map<BlockPos, Object> markers = (Map<BlockPos, Object>) MARKERS_FIELD.get(renderer);
			long removalTime = Util.getMeasuringTimeMs() + Math.max(durationMs, 1);
			Object marker = MARKER_CONSTRUCTOR.newInstance(colorArgb, pos.toShortString(), removalTime);
			markers.put(pos.toImmutable(), marker);
		}
		catch (ReflectiveOperationException exception) {
			throw new IllegalStateException("Failed to create colored highlight marker", exception);
		}
	}

	private static Field findMarkersField() {
		try {
			Field field = GameTestDebugRenderer.class.getDeclaredField("markers");
			field.setAccessible(true);
			return field;
		}
		catch (ReflectiveOperationException exception) {
			throw new ExceptionInInitializerError(exception);
		}
	}

	private static Constructor<?> findMarkerConstructor() {
		try {
			Class<?> markerClass = Class.forName("net.minecraft.client.render.debug.GameTestDebugRenderer$Marker");
			Constructor<?> constructor = markerClass.getDeclaredConstructor(int.class, String.class, long.class);
			constructor.setAccessible(true);
			return constructor;
		}
		catch (ReflectiveOperationException exception) {
			throw new ExceptionInInitializerError(exception);
		}
	}
}
