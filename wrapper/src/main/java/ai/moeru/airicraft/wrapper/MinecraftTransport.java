package ai.moeru.airicraft.wrapper;

import java.util.Map;

interface MinecraftTransport {
	Map<String, Object> getStatus();

	Map<String, Object> getFocus();

	Map<String, Object> getWorldSnapshot(Integer x, Integer y, Integer z, int radius);

	CapturedImage captureScreenshot();

	VisionDescriptionResult describeVision(String prompt);

	Map<String, Object> listWorlds();

	Map<String, Object> joinWorld(String worldId);

	Map<String, Object> listServers();

	Map<String, Object> joinServer(String serverId);

	Map<String, Object> lookAt(double x, double y, double z);

	Map<String, Object> createBlockHighlight(int x, int y, int z, String color, Long durationMs, String overlayText);

	Map<String, Object> createRegionHighlight(
		int x1,
		int y1,
		int z1,
		int x2,
		int y2,
		int z2,
		String color,
		Long durationMs,
		String overlayText
	);

	Map<String, Object> listHighlights();

	Map<String, Object> clearHighlight(String highlightId);

	Map<String, Object> clearHighlights();
}
