package ai.moeru.airicraft.agent.verification;

import java.util.List;

public abstract class VerificationScenario {
	private List<VerificationStep> cachedSteps;

	public abstract String name();

	protected abstract void define(ScenarioBuilder builder);

	public final List<VerificationStep> steps() {
		if (cachedSteps == null) {
			ScenarioBuilder builder = new ScenarioBuilder();
			define(builder);
			cachedSteps = builder.build();
		}

		return cachedSteps;
	}
}
