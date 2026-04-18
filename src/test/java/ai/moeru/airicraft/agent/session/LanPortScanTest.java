package ai.moeru.airicraft.agent.session;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LanPortScanTest {
	@Test
	void startsAtDefaultPortAndIncrementsUntilOpenSucceeds() {
		List<Integer> attempts = new ArrayList<>();

		int port = LanPortScan.openFirstAvailable(25565, 25568, candidate -> {
			attempts.add(candidate);
			return candidate == 25567;
		});

		assertEquals(25567, port);
		assertEquals(List.of(25565, 25566, 25567), attempts);
	}

	@Test
	void failsAfterTryingFullRange() {
		List<Integer> attempts = new ArrayList<>();

		assertThrows(LanPortScan.LanPortUnavailableException.class, () ->
			LanPortScan.openFirstAvailable(25565, 25567, candidate -> {
				attempts.add(candidate);
				return false;
			})
		);
		assertEquals(List.of(25565, 25566, 25567), attempts);
	}
}
