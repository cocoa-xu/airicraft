package ai.moeru.airicraft.agent.follow;

import ai.moeru.airicraft.agent.events.SemanticEventBuffer;
import ai.moeru.airicraft.agent.goals.GoalSnapshot;
import ai.moeru.airicraft.agent.goals.GoalType;
import ai.moeru.airicraft.agent.session.SessionMode;
import ai.moeru.airicraft.agent.session.SessionSnapshot;
import ai.moeru.airicraft.agent.social.NearbyPlayerTracker;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FollowCapabilityTest {
	@Test
	void preservesTargetTrackingInSingleplayerLocalWhileActuationIsBlocked() {
		FollowCapability capability = new FollowCapability();
		NearbyPlayerTracker tracker = new NearbyPlayerTracker();
		SemanticEventBuffer eventBuffer = new SemanticEventBuffer(16);
		SessionSnapshot session = new SessionSnapshot(
			SessionMode.SINGLEPLAYER_LOCAL,
			true,
			true,
			"minecraft:overworld",
			false,
			-1,
			10L
		);
		GoalSnapshot goal = new GoalSnapshot(GoalType.FOLLOW_PLAYER, "Alice", 10L, "test");

		assertFalse(session.companionActuationAllowed());

		tracker.injectPlayerNearby("Alice", new Vec3d(5.0D, 64.0D, 0.0D), 10L, eventBuffer);
		FollowState acquired = capability.tick(null, session, Optional.of(goal), tracker, 11L, eventBuffer);

		assertTrue(acquired.goalActive());
		assertTrue(acquired.targetNearby());
		assertTrue(eventBuffer.containsTypeForPlayer("follow.target_acquired", "Alice"));

		tracker.injectPlayerDisconnect("Alice", 12L, eventBuffer);
		FollowState lost = capability.tick(null, session, Optional.of(goal), tracker, 13L, eventBuffer);

		assertTrue(lost.goalActive());
		assertFalse(lost.targetNearby());
		assertEquals("Alice", lost.targetPlayer());
		assertTrue(eventBuffer.containsTypeForPlayer("follow.target_lost", "Alice"));
	}
}
