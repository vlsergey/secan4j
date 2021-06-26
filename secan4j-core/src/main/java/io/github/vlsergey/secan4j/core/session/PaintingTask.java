package io.github.vlsergey.secan4j.core.session;

import io.github.vlsergey.secan4j.core.colored.ColoredObject;
import javassist.CtBehavior;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode.Exclude;
import lombok.NonNull;

@AllArgsConstructor
@Data
class PaintingTask implements Comparable<PaintingTask>, Runnable {

	@FunctionalInterface
	public interface TaskExecution {

		void run(PaintingTask task);

	}

	private final @NonNull CtBehavior ctMethod;
	private final ColoredObject[] paintedIns;
	private final ColoredObject[] paintedOuts;

	@Exclude
	private final long priority;

	@Exclude
	private final TaskExecution taskToExecute;

	@Exclude
	private final long versionOfHeap;

	@Override
	public int compareTo(PaintingTask o) {
		return Long.compare(this.priority, o.priority);
	}

	@Override
	public void run() {
		this.taskToExecute.run(this);
	}

	public PaintingTask withPriorityAndVersionOfHeap(final long priority, final long versionOfHeap) {
		return new PaintingTask(ctMethod, paintedIns, paintedOuts, priority, taskToExecute, versionOfHeap);
	}
}