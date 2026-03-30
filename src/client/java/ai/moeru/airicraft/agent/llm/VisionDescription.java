package ai.moeru.airicraft.agent.llm;

public record VisionDescription(
	String text,
	String model,
	long capturedAtMs
) {
}
