package ai.moeru.airicraft.agent.events;

import java.util.List;

public record SemanticEventQueryResult(
	long oldestSeqNo,
	long latestSeqNo,
	boolean truncated,
	List<SemanticEvent> events
) {
}
