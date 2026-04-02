package ai.moeru.airicraft.agent.verification.scenarios;

import ai.moeru.airicraft.agent.verification.ScenarioBuilder;
import ai.moeru.airicraft.agent.verification.VerificationScenario;

import java.util.Objects;
import java.util.function.BooleanSupplier;

public final class ManualInputIdlePassthroughVerification extends VerificationScenario {
	private final BooleanSupplier singleplayerLocal;
	private final BooleanSupplier noActiveGoal;
	private final Runnable pressForwardKey;
	private final BooleanSupplier forwardKeyPressed;
	private final Runnable releaseForwardKey;

	public ManualInputIdlePassthroughVerification(
		BooleanSupplier singleplayerLocal,
		BooleanSupplier noActiveGoal,
		Runnable pressForwardKey,
		BooleanSupplier forwardKeyPressed,
		Runnable releaseForwardKey
	) {
		this.singleplayerLocal = Objects.requireNonNull(singleplayerLocal, "singleplayerLocal");
		this.noActiveGoal = Objects.requireNonNull(noActiveGoal, "noActiveGoal");
		this.pressForwardKey = Objects.requireNonNull(pressForwardKey, "pressForwardKey");
		this.forwardKeyPressed = Objects.requireNonNull(forwardKeyPressed, "forwardKeyPressed");
		this.releaseForwardKey = Objects.requireNonNull(releaseForwardKey, "releaseForwardKey");
	}

	@Override
	public String name() {
		return "manual_input.idle_passthrough";
	}

	@Override
	protected void define(ScenarioBuilder builder) {
		builder
			.require("in singleplayer local", singleplayerLocal)
			.require("no active goal", noActiveGoal)
			.action("press forward key", pressForwardKey)
			.waitUntil("forward key stays pressed across idle tick", 5, forwardKeyPressed)
			.action("release forward key", releaseForwardKey);
	}
}
