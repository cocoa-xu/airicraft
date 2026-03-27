package ai.moeru.airicraft.agent.events;

import java.util.Map;

public record SemanticEvent(
	long seqNo,
	long tick,
	String type,
	Map<String, Object> payload
) {
}
