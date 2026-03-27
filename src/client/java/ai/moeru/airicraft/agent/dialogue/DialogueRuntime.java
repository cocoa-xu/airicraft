package ai.moeru.airicraft.agent.dialogue;

import java.util.Optional;

public final class DialogueRuntime {
	private DialogueResponse lastResponse;
	private boolean pendingReply;

	public void recordResponse(DialogueResponse response) {
		lastResponse = response;
		pendingReply = true;
	}

	public Optional<DialogueResponse> lastResponse() {
		return Optional.ofNullable(lastResponse);
	}

	public boolean hasPendingReply() {
		return pendingReply;
	}

	public void markReplyObserved() {
		pendingReply = false;
	}

	public void clear() {
		lastResponse = null;
		pendingReply = false;
	}
}
