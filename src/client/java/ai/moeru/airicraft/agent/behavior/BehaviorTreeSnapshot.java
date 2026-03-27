package ai.moeru.airicraft.agent.behavior;

import ai.moeru.airicraft.agent.control.MovementStateSnapshot;

import java.util.List;

public record BehaviorTreeSnapshot(
	NodeStatus status,
	List<String> activeNodePath,
	MovementStateSnapshot movement
) {
	public static BehaviorTreeSnapshot idle() {
		return new BehaviorTreeSnapshot(NodeStatus.IDLE, List.of("Root", "Idle"), MovementStateSnapshot.idle());
	}
}
