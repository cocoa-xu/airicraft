package ai.moeru.airicraft.agent.llm;

import java.util.Objects;

public record LlmImageAttachment(
	String mimeType,
	byte[] imageBytes,
	String detail
) {
	public LlmImageAttachment {
		mimeType = Objects.requireNonNull(mimeType, "mimeType");
		imageBytes = imageBytes.clone();
		detail = Objects.requireNonNull(detail, "detail");
	}

	@Override
	public byte[] imageBytes() {
		return imageBytes.clone();
	}
}
