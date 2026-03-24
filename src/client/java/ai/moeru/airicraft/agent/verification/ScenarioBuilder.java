package ai.moeru.airicraft.agent.verification;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

public final class ScenarioBuilder {
	private final List<VerificationStep> steps = new ArrayList<>();

	public ScenarioBuilder require(String description, BooleanSupplier predicate) {
		steps.add(new VerificationStep(VerificationStepType.REQUIRE, description, 0, predicate, null));
		return this;
	}

	public ScenarioBuilder action(String description, Runnable action) {
		steps.add(new VerificationStep(VerificationStepType.ACTION, description, 0, null, action));
		return this;
	}

	public ScenarioBuilder waitUntil(String description, int timeoutTicks, BooleanSupplier predicate) {
		steps.add(new VerificationStep(VerificationStepType.WAIT_UNTIL, description, timeoutTicks, predicate, null));
		return this;
	}

	public ScenarioBuilder assertThat(String description, BooleanSupplier predicate) {
		steps.add(new VerificationStep(VerificationStepType.ASSERT, description, 0, predicate, null));
		return this;
	}

	List<VerificationStep> build() {
		return List.copyOf(steps);
	}
}
