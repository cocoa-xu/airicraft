package ai.moeru.airicraft.agent.llm;

public record VisionRequest(
	String prompt,
	String mimeType,
	byte[] imageBytes,
	long capturedAtMs
) {
	public VisionRequest {
		imageBytes = imageBytes.clone();
	}

	@Override
	public byte[] imageBytes() {
		return imageBytes.clone();
	}
}
