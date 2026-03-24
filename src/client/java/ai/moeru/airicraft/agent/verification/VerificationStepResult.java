package ai.moeru.airicraft.agent.verification;

public record VerificationStepResult(
	String description,
	VerificationStatus status,
	int waitedTicks,
	String message
) {
}
