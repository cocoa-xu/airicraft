package ai.moeru.airicraft.agent.social;

import java.util.UUID;

public record NearbyPlayerSnapshot(
	UUID uuid,
	String name,
	double x,
	double y,
	double z,
	boolean injected
) {
}
