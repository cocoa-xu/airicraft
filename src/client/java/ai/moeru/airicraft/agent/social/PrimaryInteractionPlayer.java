package ai.moeru.airicraft.agent.social;

import java.util.UUID;

public record PrimaryInteractionPlayer(
	UUID uuid,
	String name,
	long lastSpokeTick
) {
}
