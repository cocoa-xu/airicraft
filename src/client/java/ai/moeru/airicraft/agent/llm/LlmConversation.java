package ai.moeru.airicraft.agent.llm;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record LlmConversation(List<LlmChatMessage> messages) {
	public LlmConversation {
		messages = List.copyOf(Objects.requireNonNull(messages, "messages"));
	}

	public static LlmConversation of(List<LlmChatMessage> messages) {
		return new LlmConversation(messages);
	}

	public LlmConversation withAppended(LlmChatMessage message) {
		ArrayList<LlmChatMessage> updated = new ArrayList<>(messages);
		updated.add(Objects.requireNonNull(message, "message"));
		return new LlmConversation(updated);
	}
}
