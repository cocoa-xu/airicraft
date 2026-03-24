package ai.moeru.airicraft.agent.verification.scenarios;

import ai.moeru.airicraft.agent.session.SessionMode;
import ai.moeru.airicraft.agent.verification.ScenarioBuilder;
import ai.moeru.airicraft.agent.verification.VerificationScenario;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public final class SessionVerification extends VerificationScenario {
	private final Supplier<SessionMode> sessionModeSupplier;
	private final Runnable joinWorldAction;
	private final Runnable leaveWorldAction;
	private final BooleanSupplier worldLoadedEventSeen;

	public SessionVerification(
		Supplier<SessionMode> sessionModeSupplier,
		Runnable joinWorldAction,
		Runnable leaveWorldAction,
		BooleanSupplier worldLoadedEventSeen
	) {
		this.sessionModeSupplier = Objects.requireNonNull(sessionModeSupplier, "sessionModeSupplier");
		this.joinWorldAction = Objects.requireNonNull(joinWorldAction, "joinWorldAction");
		this.leaveWorldAction = Objects.requireNonNull(leaveWorldAction, "leaveWorldAction");
		this.worldLoadedEventSeen = Objects.requireNonNull(worldLoadedEventSeen, "worldLoadedEventSeen");
	}

	@Override
	public String name() {
		return "session.basic";
	}

	@Override
	protected void define(ScenarioBuilder builder) {
		builder
			.require("client booted, out of world", () -> sessionModeSupplier.get() == SessionMode.OUT_OF_WORLD)
			.action("join world", joinWorldAction)
			.waitUntil("world loaded", 300, () -> sessionModeSupplier.get() == SessionMode.SINGLEPLAYER_LOCAL)
			.assertThat("world loaded event seen", worldLoadedEventSeen)
			.action("leave world", leaveWorldAction)
			.waitUntil("returned to menu", 300, () -> sessionModeSupplier.get() == SessionMode.OUT_OF_WORLD);
	}
}
