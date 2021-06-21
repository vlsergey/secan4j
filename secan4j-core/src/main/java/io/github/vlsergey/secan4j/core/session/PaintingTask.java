package io.github.vlsergey.secan4j.core.session;

import io.github.vlsergey.secan4j.core.colored.PathAndColor;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode.Exclude;

@AllArgsConstructor
@Data
class PaintingTask implements Comparable<PaintingTask>, Runnable {

	@FunctionalInterface
	public interface TaskExecution {

		void run(PaintingTask task);

	}

	private final String className;
	private final String methodName;
	private final String methodSignature;
	private final PathAndColor[] paintedIns;
	private final PathAndColor[] paintedOuts;

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
		return new PaintingTask(className, methodName, methodSignature, paintedIns, paintedOuts, priority,
				taskToExecute, versionOfHeap);
	}
}