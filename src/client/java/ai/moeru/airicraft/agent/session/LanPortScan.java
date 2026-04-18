package ai.moeru.airicraft.agent.session;

import java.util.function.IntPredicate;

final class LanPortScan {
	static final int DEFAULT_PORT = 25565;
	static final int MAX_PORT = 65535;

	private LanPortScan() {
	}

	static int openFirstAvailable(IntPredicate openAttempt) {
		return openFirstAvailable(DEFAULT_PORT, MAX_PORT, openAttempt);
	}

	static int openFirstAvailable(int firstPort, int lastPort, IntPredicate openAttempt) {
		for (int port = firstPort; port <= lastPort; port++) {
			if (openAttempt.test(port)) {
				return port;
			}
		}
		throw new LanPortUnavailableException("No LAN port available from " + firstPort + " to " + lastPort);
	}

	static final class LanPortUnavailableException extends RuntimeException {
		LanPortUnavailableException(String message) {
			super(message);
		}
	}
}
