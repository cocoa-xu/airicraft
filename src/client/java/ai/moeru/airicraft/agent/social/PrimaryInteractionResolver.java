package ai.moeru.airicraft.agent.social;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class PrimaryInteractionResolver {
	private final long inactivityTimeoutTicks;

	private PrimaryInteractionPlayer current;

	public PrimaryInteractionResolver(long inactivityTimeoutTicks) {
		this.inactivityTimeoutTicks = inactivityTimeoutTicks;
	}

	public void onPlayerSpoke(NearbyPlayerSnapshot player, long tick) {
		Objects.requireNonNull(player, "player");
		current = new PrimaryInteractionPlayer(player.uuid(), player.name(), tick);
	}

	public void expireInactive(long tick) {
		if (current == null) {
			return;
		}
		if (tick - current.lastSpokeTick() >= inactivityTimeoutTicks) {
			current = null;
		}
	}

	public void clearIfNotNearby(UUID playerUuid, boolean stillNearby) {
		if (current == null) {
			return;
		}
		if (current.uuid().equals(playerUuid) && !stillNearby) {
			current = null;
		}
	}

	public void clear() {
		current = null;
	}

	public Optional<PrimaryInteractionPlayer> current() {
		return Optional.ofNullable(current);
	}
}
