package ai.moeru.airicraft.agent.llm;

import java.util.Objects;

public record PlannerContextEntry(
	PlannerContextEntryType type,
	String speaker,
	String text,
	long tick,
	long timestampMs
) {
	public PlannerContextEntry {
		type = Objects.requireNonNull(type, "type");
		text = Objects.requireNonNull(text, "text");
	}
}
