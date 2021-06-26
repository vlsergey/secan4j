package io.github.vlsergey.secan4j.core.session;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PaintingExecutorService<K, T> {

	private final @NonNull Consumer<T> callback;

	private final @NonNull ExecutorService executorService;

	private final @NonNull Function<T, K> keyFunction;

	private final @NonNull Map<K, Future<?>> queued = new LinkedHashMap<>();

	public PaintingExecutorService(final Function<T, K> keyFunction, final Consumer<T> callback) {
		this.keyFunction = keyFunction;
		this.callback = callback;

//		final int availableProcessors = Runtime.getRuntime().availableProcessors();
		final int availableProcessors = 1;
		this.executorService = Executors.newFixedThreadPool(availableProcessors);
	}

	public synchronized Future<?> queue(T task) {
		final K key = keyFunction.apply(task);

		Future<?> future = queued.get(key);
		if (future != null && !future.isDone()) {
			return future;
		}

		future = executorService.submit(() -> {
			try {
				callback.accept(task);
			} catch (Throwable exc) {
				log.error("Unhandled exception: " + exc, exc);
			} finally {
				synchronized (PaintingExecutorService.this) {
					queued.remove(key);
				}
			}
		});
		queued.put(key, future);

		return future;
	}

	public void waitForAllTasksToComplete() {
		Optional<Future<?>> op;
		do {
			synchronized (this) {
				op = queued.values().stream().filter(Predicate.not(Future::isDone)).findAny();
			}
			try {
				op.get().get();
			} catch (Exception exc) {
				// ignore
			}
		} while (op.isPresent());
	}

}
