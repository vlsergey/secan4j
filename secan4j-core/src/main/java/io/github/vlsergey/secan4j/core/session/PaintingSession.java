package io.github.vlsergey.secan4j.core.session;

import static java.util.Collections.emptyMap;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;

import io.github.vlsergey.secan4j.core.colored.GraphColorer;
import io.github.vlsergey.secan4j.core.colored.PathToClassesAndColor;
import io.github.vlsergey.secan4j.core.colored.TraceItem;
import io.github.vlsergey.secan4j.core.colorless.ColorlessGraphBuilder;
import io.github.vlsergey.secan4j.core.colorless.Invocation;
import io.github.vlsergey.secan4j.core.user2command.UserToCommandInjectionColorer;
import io.github.vlsergey.secan4j.data.DataProvider;
import javassist.ClassPool;
import javassist.CtBehavior;
import javassist.CtClass;
import javassist.NotFoundException;
import lombok.Data;
import lombok.NonNull;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
public class PaintingSession {

	@Data
	private static final class QueuedTask {
		private final @NonNull Future<?> future;
		private final @NonNull PaintingTask task;
	}

	private final @NonNull ClassPool classPool;

	private final @NonNull AtomicLong currentHeapVersion = new AtomicLong(0);

	@Delegate
	private final @NonNull ThreadPoolExecutor executorService;

	private final @NonNull GraphColorer graphColorer;

	private final @NonNull Map<PaintingTask, List<PaintingTask>> listeners = new HashMap<PaintingTask, List<PaintingTask>>();

	private final @NonNull BiConsumer<TraceItem, TraceItem> onSourceSinkIntersection;

	private final WeakHashMap<PaintingTask, QueuedTask> queued = new WeakHashMap<>();

	// replace with quite big cache?
	private final @NonNull Map<PaintingTask, PaintingTaskResult> results = new ConcurrentHashMap<PaintingTask, PaintingTaskResult>();

	public PaintingSession(final @NonNull ClassPool classPool,
			final @NonNull BiConsumer<TraceItem, TraceItem> onSourceSinkIntersection) {
		this.onSourceSinkIntersection = onSourceSinkIntersection;

//		final int availableProcessors = Runtime.getRuntime().availableProcessors();
		final int availableProcessors = 1;
		this.executorService = new ThreadPoolExecutor(availableProcessors, availableProcessors, 0L,
				TimeUnit.MILLISECONDS, new PriorityBlockingQueue<>()) {

			@Override
			protected <T> RunnableFuture<T> newTaskFor(Runnable runnable, T value) {
				return new ComparableFutureTask<T>((PaintingTask) runnable, value);
			}

		};

		this.classPool = classPool;
		this.graphColorer = new GraphColorer(classPool, new ColorlessGraphBuilder(),
				new UserToCommandInjectionColorer(new DataProvider()));
	}

	@SuppressWarnings("unchecked")
	public void analyze(CtBehavior ctMethod) throws ExecutionException, InterruptedException {
		PaintingTask paintingTask = new PaintingTask(ctMethod, null, null, 0, this::executeTask, 0);

		Optional<Future<?>> op = Optional.of(queueImpl(paintingTask));
		while (op.isPresent()) {
			try {
				op.get().get();
			} catch (Exception exc) {
				// ignore
			}
			synchronized (this) {
				op = (Optional<Future<?>>) (Object) queued.values().stream().map(QueuedTask::getFuture)
						.filter(f -> !f.isDone()).findAny();
			}
		}
	}

	protected @NonNull void executeTask(final @NonNull PaintingTask task) {
		if (log.isDebugEnabled())
			log.debug("(Re)coloring {}...", task.getCtMethod().getLongName());

		try {
			// XXX: push down to arg type?
			final @NonNull CtClass ctClass = task.getCtMethod().getDeclaringClass();

			final long usedHeapVersion = currentHeapVersion.get();
			Optional<PathToClassesAndColor[][]> opUpdated = graphColorer.color(ctClass, task.getCtMethod(), task.getPaintedIns(),
					task.getPaintedOuts(), (subInvokation, ins, outs) -> {
						if (task.getCtMethod().isEmpty()) {
							// virtual, so far ignore
							return emptyMap();
						}

						log.debug("Going deeper from {} by analyzing {}.{} call", task.getCtMethod().getName(),
								subInvokation.getClassName(), subInvokation.getMethodName());
						try {
							queue(subInvokation, ins, outs, task.getPriority() - 1, task);
							// XXX: actually return result
							return emptyMap();
						} catch (Exception exc) {
							log.warn("Unable to go deeper: " + exc.getMessage(), exc);
							return emptyMap();
						}
					}, onSourceSinkIntersection);
			if (opUpdated.isEmpty()) {
				log.warn("No results for deeper travel to {}", task.getCtMethod());
				return;
			}
			final @NonNull PathToClassesAndColor[][] updated = opUpdated.get();

			final PaintingTaskResult prevResults = results.get(task);
			if (prevResults == null || !Arrays.equals(prevResults.getPaintedIns(), updated[0])
					|| !Arrays.equals(prevResults.getPaintedOuts(), updated[1])
					|| prevResults.getVersionOfHeap() < usedHeapVersion) {
				if (log.isDebugEnabled())
					log.debug("Colors were changed after recheking {}. Store new result and invoke update listeners",
							task.getCtMethod().getLongName());

				results.put(task, new PaintingTaskResult(updated[0], updated[1], usedHeapVersion));

				synchronized (this) {
					final List<PaintingTask> taskListeners = listeners.get(task);
					if (taskListeners != null) {
						for (PaintingTask toRequeue : taskListeners) {
							queueImpl(toRequeue);
						}
					}
				}
			} else {
				if (log.isDebugEnabled())
					log.debug("Colors didn't changed after recheking {}, drop update listeners",
							task.getCtMethod().getLongName());
				listeners.remove(task);
			}

		} catch (Exception exc) {
			log.error("Unable to execute colorizing task for " + task.getCtMethod().getLongName() + ": "
					+ exc.getMessage(), exc);
			// no, we don't requeue after error
		}
	}

	public void queue(Invocation invocation, PathToClassesAndColor[] paintedIns, PathToClassesAndColor[] paintedOuts, long priority,
			PaintingTask toInvokeAfter) throws NotFoundException {

		CtClass invClass = classPool.get(invocation.getClassName());
		CtBehavior invMethod = invocation.getMethodName().equals("<init>")
				? invClass.getConstructor(invocation.getMethodSignature())
				: invClass.getMethod(invocation.getMethodName(), invocation.getMethodSignature());

		synchronized (this) {
			queueImpl(new PaintingTask(invMethod, paintedIns, paintedOuts, priority, this::executeTask,
					currentHeapVersion.get()));
		}
	}

	private synchronized Future<?> queueImpl(final PaintingTask toQueue) {
		final PaintingTaskResult alreadyCalculated = this.results.get(toQueue);
		if (alreadyCalculated != null) {
			if (alreadyCalculated.getVersionOfHeap() >= toQueue.getVersionOfHeap()) {
				log.debug("We have results for {}() and they are fresh enought", toQueue.getCtMethod().getName());
				return CompletableFuture.completedFuture(null);
			}
			log.debug("We have results for {}(), but they need to be updated with new version of heap ({} < {})",
					toQueue.getCtMethod().getName(), alreadyCalculated.getVersionOfHeap(), toQueue.getVersionOfHeap());
		}

		final QueuedTask alreadyQueued = queued.get(toQueue);
		if (alreadyQueued != null) {
			PaintingTask existed = alreadyQueued.getTask();
			if (existed.equals(toQueue) && (existed.getPriority() < toQueue.getPriority()
					|| existed.getVersionOfHeap() < toQueue.getVersionOfHeap())) {

				queued.remove(existed);
				alreadyQueued.getFuture().cancel(false);
			}
		}

		final Future<?> future = executorService.submit(toQueue);
		queued.put(toQueue, new QueuedTask(future, toQueue));
		return future;
	}
}
