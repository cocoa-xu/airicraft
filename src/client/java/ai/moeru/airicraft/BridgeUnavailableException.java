package ai.moeru.airicraft;

public final class BridgeUnavailableException extends RuntimeException {
	private final String code;

	public BridgeUnavailableException(String code, String message) {
		super(message);
		this.code = code;
	}

	public String code() {
		return code;
	}
}
