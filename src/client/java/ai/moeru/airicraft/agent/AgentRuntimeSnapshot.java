package ai.moeru.airicraft.agent;

import ai.moeru.airicraft.agent.session.SessionSnapshot;
import ai.moeru.airicraft.agent.verification.VerificationReport;

public record AgentRuntimeSnapshot(
	boolean initialized,
	long tickCount,
	SessionSnapshot session,
	VerificationReport verification
) {
}
