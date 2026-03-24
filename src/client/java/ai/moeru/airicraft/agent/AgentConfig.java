package ai.moeru.airicraft.agent;

public record AgentConfig(
	boolean verificationEnabled,
	boolean verificationAutoRunAll
) {
	public static AgentConfig defaults() {
		return new AgentConfig(false, false);
	}
}
