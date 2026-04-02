package ai.moeru.airicraft.agent.session;

public record SessionSnapshot(
	SessionMode mode,
	boolean clientBooted,
	boolean worldLoaded,
	String dimensionId,
	boolean lanPublished,
	int lanPort,
	long tickCount
) {
	public static SessionSnapshot initial() {
		return new SessionSnapshot(SessionMode.OUT_OF_WORLD, false, false, null, false, 0, 0L);
	}

	public boolean companionActuationAllowed() {
		return mode == SessionMode.SINGLEPLAYER_LAN_HOST || mode == SessionMode.REMOTE_MULTIPLAYER;
	}

	public SessionSnapshot withMode(SessionMode value) {
		return new SessionSnapshot(value, clientBooted, worldLoaded, dimensionId, lanPublished, lanPort, tickCount);
	}

	public SessionSnapshot withClientBooted(boolean value) {
		return new SessionSnapshot(mode, value, worldLoaded, dimensionId, lanPublished, lanPort, tickCount);
	}

	public SessionSnapshot withWorldLoaded(boolean value) {
		return new SessionSnapshot(mode, clientBooted, value, dimensionId, lanPublished, lanPort, tickCount);
	}

	public SessionSnapshot withDimensionId(String value) {
		return new SessionSnapshot(mode, clientBooted, worldLoaded, value, lanPublished, lanPort, tickCount);
	}

	public SessionSnapshot withLanPublished(boolean value) {
		return new SessionSnapshot(mode, clientBooted, worldLoaded, dimensionId, value, lanPort, tickCount);
	}

	public SessionSnapshot withLanPort(int value) {
		return new SessionSnapshot(mode, clientBooted, worldLoaded, dimensionId, lanPublished, value, tickCount);
	}

	public SessionSnapshot withTickCount(long value) {
		return new SessionSnapshot(mode, clientBooted, worldLoaded, dimensionId, lanPublished, lanPort, value);
	}
}
