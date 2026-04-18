package ai.moeru.airicraft.agent.session;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoLanOpenStateTest {
	@Test
	void attemptsOnlyForUnfailedLocalSingleplayerSession() {
		AutoLanOpenState state = new AutoLanOpenState();

		assertFalse(state.shouldAttempt(snapshot(SessionMode.OUT_OF_WORLD)));
		assertTrue(state.shouldAttempt(snapshot(SessionMode.SINGLEPLAYER_LOCAL)));

		state.recordFailure();

		assertFalse(state.shouldAttempt(snapshot(SessionMode.SINGLEPLAYER_LOCAL)));
	}

	@Test
	void clearAllowsNewWorldSessionToRetry() {
		AutoLanOpenState state = new AutoLanOpenState();

		state.recordFailure();
		state.clear();

		assertTrue(state.shouldAttempt(snapshot(SessionMode.SINGLEPLAYER_LOCAL)));
	}

	private static SessionSnapshot snapshot(SessionMode mode) {
		return new SessionSnapshot(
			mode,
			true,
			mode != SessionMode.OUT_OF_WORLD,
			mode == SessionMode.OUT_OF_WORLD ? null : "minecraft:overworld",
			mode == SessionMode.SINGLEPLAYER_LAN_HOST,
			mode == SessionMode.SINGLEPLAYER_LAN_HOST ? 25565 : 0,
			10L
		);
	}
}
