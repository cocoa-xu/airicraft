package ai.moeru.airicraft.agent.llm;

import java.util.Objects;

public record LlmChatMessage(
	String role,
	String content,
	LlmMessageKind kind
) {
	public LlmChatMessage {
		role = Objects.requireNonNull(role, "role");
		content = Objects.requireNonNull(content, "content");
		kind = Objects.requireNonNull(kind, "kind");
	}

	public static LlmChatMessage system(String content) {
		return new LlmChatMessage("system", content, LlmMessageKind.SYSTEM);
	}

	public static LlmChatMessage user(String content, LlmMessageKind kind) {
		return new LlmChatMessage("user", content, kind);
	}

	public static LlmChatMessage assistant(String content) {
		return new LlmChatMessage("assistant", content, LlmMessageKind.ASSISTANT_TURN);
	}
}
