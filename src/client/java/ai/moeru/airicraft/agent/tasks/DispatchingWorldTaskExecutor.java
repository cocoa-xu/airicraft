package ai.moeru.airicraft.agent.tasks;

import ai.moeru.airicraft.agent.session.SessionSnapshot;

import java.util.Objects;
import java.util.Optional;

public final class DispatchingWorldTaskExecutor implements WorldTaskExecutor {
	private final WorldTaskExecutor baritoneExecutor;
	private final WorldTaskExecutor craftingExecutor;
	private final WorldTaskExecutor dropItemsExecutor;
	private WorldTaskType activeType;

	public DispatchingWorldTaskExecutor(WorldTaskExecutor baritoneExecutor, WorldTaskExecutor craftingExecutor) {
		this(baritoneExecutor, craftingExecutor, new DropItemsTaskExecutor());
	}

	public DispatchingWorldTaskExecutor(WorldTaskExecutor baritoneExecutor, WorldTaskExecutor craftingExecutor, WorldTaskExecutor dropItemsExecutor) {
		this.baritoneExecutor = Objects.requireNonNull(baritoneExecutor, "baritoneExecutor");
		this.craftingExecutor = Objects.requireNonNull(craftingExecutor, "craftingExecutor");
		this.dropItemsExecutor = Objects.requireNonNull(dropItemsExecutor, "dropItemsExecutor");
	}

	@Override
	public Optional<TaskTerminalEvent> tick(SessionSnapshot sessionSnapshot, Optional<WorldTaskRequest> activeTask) {
		if (activeTask.isEmpty()) {
			activeType = null;
			baritoneExecutor.tick(sessionSnapshot, Optional.empty());
			craftingExecutor.tick(sessionSnapshot, Optional.empty());
			dropItemsExecutor.tick(sessionSnapshot, Optional.empty());
			return Optional.empty();
		}

		WorldTaskRequest request = activeTask.get();
		activeType = request.type();
		if (request.type() == WorldTaskType.CRAFT_RECIPE) {
			baritoneExecutor.tick(sessionSnapshot, Optional.empty());
			dropItemsExecutor.tick(sessionSnapshot, Optional.empty());
			return craftingExecutor.tick(sessionSnapshot, activeTask);
		}
		if (request.type() == WorldTaskType.DROP_ITEMS) {
			baritoneExecutor.tick(sessionSnapshot, Optional.empty());
			craftingExecutor.tick(sessionSnapshot, Optional.empty());
			return dropItemsExecutor.tick(sessionSnapshot, activeTask);
		}

		craftingExecutor.tick(sessionSnapshot, Optional.empty());
		dropItemsExecutor.tick(sessionSnapshot, Optional.empty());
		return baritoneExecutor.tick(sessionSnapshot, activeTask);
	}

	@Override
	public TaskExecutionSnapshot snapshot() {
		if (activeType == WorldTaskType.CRAFT_RECIPE) {
			return craftingExecutor.snapshot();
		}
		if (activeType == WorldTaskType.DROP_ITEMS) {
			return dropItemsExecutor.snapshot();
		}
		if (activeType != null) {
			return baritoneExecutor.snapshot();
		}
		return TaskExecutionSnapshot.idle();
	}

	@Override
	public void onWorldLeave() {
		activeType = null;
		baritoneExecutor.onWorldLeave();
		craftingExecutor.onWorldLeave();
		dropItemsExecutor.onWorldLeave();
	}

	@Override
	public void shutdown() {
		activeType = null;
		baritoneExecutor.shutdown();
		craftingExecutor.shutdown();
		dropItemsExecutor.shutdown();
	}
}
