package ai.moeru.airicraft.agent.llm;

import java.util.Objects;

public record LlmCallResult<T>(
	T payload,
	LlmUsageSnapshot usage
) {
	public LlmCallResult {
		usage = usage == null ? LlmUsageSnapshot.unknown() : usage;
	}

	public static <T> LlmCallResult<T> of(T payload, LlmUsageSnapshot usage) {
		return new LlmCallResult<>(payload, Objects.requireNonNullElse(usage, LlmUsageSnapshot.unknown()));
	}
}
