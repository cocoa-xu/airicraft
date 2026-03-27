package ai.moeru.airicraft.agent.verification.scenarios;

import ai.moeru.airicraft.agent.session.SessionMode;
import ai.moeru.airicraft.agent.verification.ScenarioBuilder;
import ai.moeru.airicraft.agent.verification.VerificationScenario;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public final class SessionLanVerification extends VerificationScenario {
	private final Supplier<SessionMode> sessionModeSupplier;
	private final Runnable openLanAction;
	private final BooleanSupplier lanPortAvailable;
	private final BooleanSupplier lanOpenedEventSeen;

	public SessionLanVerification(
		Supplier<SessionMode> sessionModeSupplier,
		Runnable openLanAction,
		BooleanSupplier lanPortAvailable,
		BooleanSupplier lanOpenedEventSeen
	) {
		this.sessionModeSupplier = Objects.requireNonNull(sessionModeSupplier, "sessionModeSupplier");
		this.openLanAction = Objects.requireNonNull(openLanAction, "openLanAction");
		this.lanPortAvailable = Objects.requireNonNull(lanPortAvailable, "lanPortAvailable");
		this.lanOpenedEventSeen = Objects.requireNonNull(lanOpenedEventSeen, "lanOpenedEventSeen");
	}

	@Override
	public String name() {
		return "session.lan";
	}

	@Override
	protected void define(ScenarioBuilder builder) {
		builder
			.require("in singleplayer local", () -> sessionModeSupplier.get() == SessionMode.SINGLEPLAYER_LOCAL)
			.action("open lan", openLanAction)
			.waitUntil("lan host active", 100, () -> sessionModeSupplier.get() == SessionMode.SINGLEPLAYER_LAN_HOST)
			.assertThat("lan port available", lanPortAvailable)
			.assertThat("lan opened event seen", lanOpenedEventSeen);
	}
}
