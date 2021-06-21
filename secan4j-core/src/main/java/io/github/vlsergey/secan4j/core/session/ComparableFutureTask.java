package io.github.vlsergey.secan4j.core.session;

import java.util.concurrent.FutureTask;

class ComparableFutureTask<T> extends FutureTask<T> implements Comparable<ComparableFutureTask<T>> {

	private final long priority;

	public ComparableFutureTask(PaintingTask task, T value) {
		super(task, value);
		this.priority = task.getPriority();
	}

	@Override
	public int compareTo(ComparableFutureTask<T> o) {
		return Long.compare(this.priority, o.priority);
	}

}