package ai.moeru.airicraft.agent.tasks;

public record DropItemsStepArgs(
	String itemId,
	int quantity,
	String targetPlayer
) {
	public DropItemsStepArgs {
		itemId = itemId == null ? null : itemId.trim();
		targetPlayer = targetPlayer == null || targetPlayer.isBlank() ? null : targetPlayer.trim();
		if (itemId == null || itemId.isBlank()) {
			throw new IllegalArgumentException("itemId must not be blank");
		}
		if (quantity <= 0) {
			throw new IllegalArgumentException("quantity must be positive");
		}
	}
}
