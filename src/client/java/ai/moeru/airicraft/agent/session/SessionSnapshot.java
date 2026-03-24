package ai.moeru.airicraft.agent.session;

public record SessionSnapshot(
	SessionMode mode,
	boolean clientBooted,
	boolean worldLoaded,
	String dimensionId,
	long tickCount
) {
	public static SessionSnapshot initial() {
		return new SessionSnapshot(SessionMode.OUT_OF_WORLD, false, false, null, 0L);
	}

	public SessionSnapshot withMode(SessionMode value) {
		return new SessionSnapshot(value, clientBooted, worldLoaded, dimensionId, tickCount);
	}

	public SessionSnapshot withClientBooted(boolean value) {
		return new SessionSnapshot(mode, value, worldLoaded, dimensionId, tickCount);
	}

	public SessionSnapshot withWorldLoaded(boolean value) {
		return new SessionSnapshot(mode, clientBooted, value, dimensionId, tickCount);
	}

	public SessionSnapshot withDimensionId(String value) {
		return new SessionSnapshot(mode, clientBooted, worldLoaded, value, tickCount);
	}

	public SessionSnapshot withTickCount(long value) {
		return new SessionSnapshot(mode, clientBooted, worldLoaded, dimensionId, value);
	}
}
