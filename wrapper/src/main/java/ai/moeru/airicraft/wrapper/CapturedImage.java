package ai.moeru.airicraft.wrapper;

record CapturedImage(
	byte[] bytes,
	String format,
	int width,
	int height,
	int sourceWidth,
	int sourceHeight,
	long capturedAtMs
) {
	CapturedImage {
		bytes = bytes.clone();
	}

	@Override
	public byte[] bytes() {
		return bytes.clone();
	}
}
