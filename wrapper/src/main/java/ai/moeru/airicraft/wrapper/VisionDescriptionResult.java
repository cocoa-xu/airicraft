package ai.moeru.airicraft.wrapper;

record VisionDescriptionResult(
	String format,
	long capturedAtMs,
	String model,
	String description
) {
}
