package ai.moeru.airicraft;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.DrawStyle;
import net.minecraft.client.render.debug.GameTestDebugRenderer;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.debug.gizmo.GizmoDrawing;
import net.minecraft.world.debug.gizmo.TextGizmo;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class HighlightManager {
	private static final Field MARKERS_FIELD = findMarkersField();
	private static final Constructor<?> MARKER_CONSTRUCTOR = findMarkerConstructor();

	private final Map<String, HighlightRecord> highlights = new LinkedHashMap<>();

	public String addBlock(BlockPos pos, int colorArgb, Long durationMs, String overlayText) {
		long now = Util.getMeasuringTimeMs();
		purgeExpired(now);
		String highlightId = UUID.randomUUID().toString();
		highlights.put(highlightId, new BlockHighlight(
			highlightId,
			pos.toImmutable(),
			colorArgb,
			defaultText(overlayText, pos.toShortString()),
			expiresAt(now, durationMs)
		));
		rebuildBlockMarkers();
		return highlightId;
	}

	public String addRegion(BlockPos posA, BlockPos posB, int colorArgb, Long durationMs, String overlayText) {
		long now = Util.getMeasuringTimeMs();
		purgeExpired(now);
		BlockPos min = new BlockPos(
			Math.min(posA.getX(), posB.getX()),
			Math.min(posA.getY(), posB.getY()),
			Math.min(posA.getZ(), posB.getZ())
		);
		BlockPos max = new BlockPos(
			Math.max(posA.getX(), posB.getX()),
			Math.max(posA.getY(), posB.getY()),
			Math.max(posA.getZ(), posB.getZ())
		);
		String highlightId = UUID.randomUUID().toString();
		highlights.put(highlightId, new RegionHighlight(
			highlightId,
			min,
			max,
			colorArgb,
			defaultText(overlayText, min.toShortString() + " -> " + max.toShortString()),
			expiresAt(now, durationMs)
		));
		rebuildBlockMarkers();
		return highlightId;
	}

	public List<Map<String, Object>> list() {
		purgeExpired(Util.getMeasuringTimeMs());
		rebuildBlockMarkers();
		List<Map<String, Object>> payload = new ArrayList<>();
		for (HighlightRecord highlight : highlights.values()) {
			payload.add(highlight.toPayload());
		}
		return payload;
	}

	public boolean clearById(String highlightId) {
		purgeExpired(Util.getMeasuringTimeMs());
		boolean removed = highlights.remove(highlightId) != null;
		if (removed) {
			rebuildBlockMarkers();
		}
		return removed;
	}

	public int clear() {
		int count = highlights.size();
		highlights.clear();
		clearRendererMarkers();
		return count;
	}

	public void tick() {
		if (purgeExpired(Util.getMeasuringTimeMs())) {
			rebuildBlockMarkers();
		}
	}

	public void render(WorldRenderContext context) {
		purgeExpired(Util.getMeasuringTimeMs());
		var client = MinecraftClient.getInstance();
		if (client.worldRenderer == null || client.world == null) {
			return;
		}

		try (var ignored = client.worldRenderer.startDrawingGizmos()) {
			for (HighlightRecord highlight : highlights.values()) {
				if (highlight instanceof RegionHighlight region) {
					renderRegion(region);
				}
			}
		}
	}

	private void renderRegion(RegionHighlight region) {
		int strokeColor = withAlpha(region.colorArgb, 0xFF);
		Box box = Box.enclosing(region.minPos, region.maxPos).expand(0.002D);
		GizmoDrawing.box(box, DrawStyle.filledAndStroked(strokeColor, 1.0F, region.colorArgb));
		Vec3d labelPos = box.getCenter().add(0.0D, box.getLengthY() / 2.0D + 0.2D, 0.0D);
		GizmoDrawing.text(region.overlayText, labelPos, TextGizmo.Style.centered(0xFFFFFFFF).scaled(0.16F))
			.ignoreOcclusion();
	}

	private void rebuildBlockMarkers() {
		var client = MinecraftClient.getInstance();
		if (client.worldRenderer == null) {
			return;
		}

		clearRendererMarkers();

		for (HighlightRecord highlight : highlights.values()) {
			if (highlight instanceof BlockHighlight block) {
				addColoredMarker(
					client.worldRenderer.gameTestDebugRenderer,
					block.pos,
					block.colorArgb,
					block.overlayText,
					block.expiresAtEpochMillis
				);
			}
		}
	}

	private void clearRendererMarkers() {
		var client = MinecraftClient.getInstance();
		if (client.worldRenderer != null) {
			client.worldRenderer.gameTestDebugRenderer.clear();
		}
	}

	private boolean purgeExpired(long now) {
		boolean removed = highlights.values().removeIf(highlight -> highlight.isExpired(now));
		return removed;
	}

	@SuppressWarnings("unchecked")
	private static void addColoredMarker(
		GameTestDebugRenderer renderer,
		BlockPos pos,
		int colorArgb,
		String overlayText,
		Long expiresAtEpochMillis
	) {
		try {
			renderer.addMarker(pos, pos);
			Map<BlockPos, Object> markers = (Map<BlockPos, Object>) MARKERS_FIELD.get(renderer);
			long removalTime = expiresAtEpochMillis == null ? Long.MAX_VALUE : expiresAtEpochMillis;
			Object marker = MARKER_CONSTRUCTOR.newInstance(colorArgb, overlayText, removalTime);
			markers.put(pos.toImmutable(), marker);
		}
		catch (ReflectiveOperationException exception) {
			throw new IllegalStateException("Failed to create colored highlight marker", exception);
		}
	}

	private static Long expiresAt(long now, Long durationMs) {
		if (durationMs == null) {
			return null;
		}
		return now + Math.max(durationMs, 1L);
	}

	private static String defaultText(String overlayText, String fallback) {
		return overlayText == null || overlayText.isBlank() ? fallback : overlayText;
	}

	private static int withAlpha(int colorArgb, int alpha) {
		return (colorArgb & 0x00FFFFFF) | (alpha << 24);
	}

	private static Map<String, Object> blockPosPayload(BlockPos pos) {
		return Map.of("x", pos.getX(), "y", pos.getY(), "z", pos.getZ());
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

	private sealed interface HighlightRecord permits BlockHighlight, RegionHighlight {
		String highlightId();

		int colorArgb();

		String overlayText();

		Long expiresAtEpochMillis();

		Map<String, Object> toPayload();

		default boolean isExpired(long now) {
			Long expiresAtEpochMillis = expiresAtEpochMillis();
			return expiresAtEpochMillis != null && expiresAtEpochMillis <= now;
		}
	}

	private record BlockHighlight(
		String highlightId,
		BlockPos pos,
		int colorArgb,
		String overlayText,
		Long expiresAtEpochMillis
	) implements HighlightRecord {
		@Override
		public Map<String, Object> toPayload() {
			Map<String, Object> payload = new LinkedHashMap<>();
			payload.put("highlightId", highlightId);
			payload.put("kind", "block");
			payload.put("color", String.format("%08X", colorArgb));
			payload.put("overlayText", overlayText);
			payload.put("expiresAtEpochMillis", expiresAtEpochMillis);
			payload.put("pos", blockPosPayload(pos));
			return payload;
		}
	}

	private record RegionHighlight(
		String highlightId,
		BlockPos minPos,
		BlockPos maxPos,
		int colorArgb,
		String overlayText,
		Long expiresAtEpochMillis
	) implements HighlightRecord {
		@Override
		public Map<String, Object> toPayload() {
			Map<String, Object> payload = new LinkedHashMap<>();
			payload.put("highlightId", highlightId);
			payload.put("kind", "region");
			payload.put("color", String.format("%08X", colorArgb));
			payload.put("overlayText", overlayText);
			payload.put("expiresAtEpochMillis", expiresAtEpochMillis);
			payload.put("minPos", blockPosPayload(minPos));
			payload.put("maxPos", blockPosPayload(maxPos));
			return payload;
		}
	}
}
