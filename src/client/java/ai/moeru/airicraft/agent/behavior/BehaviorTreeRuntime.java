package ai.moeru.airicraft.agent.behavior;

import ai.moeru.airicraft.agent.control.LookController;
import ai.moeru.airicraft.agent.control.MovementController;
import ai.moeru.airicraft.agent.chat.ChatService;
import ai.moeru.airicraft.agent.dialogue.DialogueResponse;
import ai.moeru.airicraft.agent.follow.FollowState;
import ai.moeru.airicraft.agent.goals.GoalSnapshot;
import ai.moeru.airicraft.agent.goals.GoalType;
import ai.moeru.airicraft.agent.session.SessionSnapshot;
import ai.moeru.airicraft.agent.dialogue.DialogueRuntime;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.Optional;

public final class BehaviorTreeRuntime {
	private static final double FOLLOW_STOP_DISTANCE = 4.0D;
	private static final float LOOK_YAW_STEP = 8.0F;
	private static final float LOOK_PITCH_STEP = 6.0F;

	private final LookController lookController = new LookController();
	private final MovementController movementController = new MovementController();

	private BehaviorTreeSnapshot snapshot = BehaviorTreeSnapshot.idle();

	public void tick(
		MinecraftClient client,
		SessionSnapshot sessionSnapshot,
		DialogueRuntime dialogueRuntime,
		ChatService chatService,
		Optional<GoalSnapshot> activeGoal,
		FollowState followState,
		long tick
	) {
		if (client == null || !sessionSnapshot.worldLoaded() || client.player == null) {
			movementController.stop(client);
			snapshot = new BehaviorTreeSnapshot(NodeStatus.RUNNING, List.of("Root", "WaitForSession"), movementController.snapshot());
			return;
		}

		if (dialogueRuntime.hasPendingReply()) {
			movementController.stop(client);
			dialogueRuntime.lastResponse()
				.map(DialogueResponse::text)
				.filter(text -> chatService.send(client, text, tick))
				.ifPresent(ignored -> dialogueRuntime.markReplyObserved());
			snapshot = new BehaviorTreeSnapshot(NodeStatus.RUNNING, List.of("Root", "ReplyToPlayer"), movementController.snapshot());
			return;
		}

		if (activeGoal.isPresent() && activeGoal.get().type() == GoalType.FOLLOW_PLAYER) {
			if (!sessionSnapshot.companionActuationAllowed()) {
				movementController.stop(client);
				snapshot = new BehaviorTreeSnapshot(
					NodeStatus.RUNNING,
					List.of("Root", "FollowPlayerSubtree", "ActuationBlockedBySession"),
					movementController.snapshot()
				);
				return;
			}

			if (followState.targetNearby()) {
				Vec3d targetPos = new Vec3d(followState.targetX(), followState.targetY() + 1.62D, followState.targetZ());
				lookController.lookAt(client, targetPos, LOOK_YAW_STEP, LOOK_PITCH_STEP);
				if (followState.distanceToTarget() > FOLLOW_STOP_DISTANCE) {
					boolean jumpToUnstick = movementController.snapshot().stuck();
					movementController.moveForward(client, true, jumpToUnstick, tick);
					var movementSnapshot = movementController.snapshot();
					snapshot = new BehaviorTreeSnapshot(
						NodeStatus.RUNNING,
						movementSnapshot.stuck()
							? List.of("Root", "FollowPlayerSubtree", "MoveCloserWhenStuck")
							: List.of("Root", "FollowPlayerSubtree", "MoveCloserWhenTooFar"),
						movementSnapshot
					);
					return;
				}

				movementController.stop(client);
				snapshot = new BehaviorTreeSnapshot(
					NodeStatus.RUNNING,
					List.of("Root", "FollowPlayerSubtree", "ObserveAndWait"),
					movementController.snapshot()
				);
				return;
			}

			movementController.stop(client);
			snapshot = new BehaviorTreeSnapshot(
				NodeStatus.RUNNING,
				List.of("Root", "FollowPlayerSubtree", "ObserveAndWait"),
				movementController.snapshot()
			);
			return;
		}

		movementController.stop(client);
		snapshot = new BehaviorTreeSnapshot(NodeStatus.RUNNING, List.of("Root", "ObserveAndWait"), movementController.snapshot());
	}

	public BehaviorTreeSnapshot snapshot() {
		return snapshot;
	}

	public void stop(MinecraftClient client) {
		movementController.stop(client);
		snapshot = BehaviorTreeSnapshot.idle();
	}
}
