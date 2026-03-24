package ai.moeru.airicraft.agent.verification;

import java.util.Objects;
import java.util.function.BooleanSupplier;

public record VerificationStep(
	VerificationStepType type,
	String description,
	int timeoutTicks,
	BooleanSupplier predicate,
	Runnable action
) {
	public VerificationStep {
		Objects.requireNonNull(type, "type");
		Objects.requireNonNull(description, "description");
	}
}
