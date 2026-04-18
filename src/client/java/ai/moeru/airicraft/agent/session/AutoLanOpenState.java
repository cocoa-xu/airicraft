package ai.moeru.airicraft.agent.session;

public final class AutoLanOpenState {
	private boolean failed;

	public boolean shouldAttempt(SessionSnapshot snapshot) {
		return !failed && snapshot.worldLoaded() && snapshot.mode() == SessionMode.SINGLEPLAYER_LOCAL;
	}

	public void recordFailure() {
		failed = true;
	}

	public void clear() {
		failed = false;
	}
}
