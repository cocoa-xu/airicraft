package ai.moeru.airicraft.wrapper;

final class BridgeUnavailableException extends RuntimeException {
	private final String code;

	BridgeUnavailableException(String code, String message) {
		super(message);
		this.code = code;
	}

	String code() {
		return code;
	}
}
