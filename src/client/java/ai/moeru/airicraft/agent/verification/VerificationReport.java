package ai.moeru.airicraft.agent.verification;

import java.util.List;

public record VerificationReport(
	VerificationStatus status,
	String scenarioName,
	String message,
	List<VerificationStepResult> steps
) {
	public static VerificationReport idle() {
		return new VerificationReport(VerificationStatus.IDLE, null, null, List.of());
	}
}
