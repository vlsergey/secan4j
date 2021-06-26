package io.github.vlsergey.secan4j.core.session;

import static java.util.Collections.emptySet;
import static java.util.Collections.newSetFromMap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import io.github.vlsergey.secan4j.core.colored.ColoredObject;
import javassist.ClassPool;
import javassist.CtBehavior;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtMethod;
import javassist.NotFoundException;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.experimental.Delegate;

public class PaintingTask {

	@AllArgsConstructor
	@Data
	public static class Result {
		private final ColoredObject[] resultIns;
		private final ColoredObject[] resultOuts;
		private final long versionOfHeap;
	}

	@AllArgsConstructor(access = AccessLevel.PRIVATE)
	@Data
	public static class TaskKey {
		private final @NonNull String className;
		private final @NonNull String methodName;
		private final @NonNull String methodSignature;

		private final ColoredObject[] paramIns;
		private final ColoredObject[] paramOuts;

		public TaskKey(CtBehavior ctMethod) {
			this(ctMethod.getDeclaringClass().getName().intern(),
					ctMethod instanceof CtConstructor ? "<init>" : ((CtMethod) ctMethod).getName().intern(),
					ctMethod.getSignature().intern(), null, null);
		}

		@Override
		public TaskKey clone() {
			return new TaskKey(className, methodName, methodSignature, paramIns, paramOuts);
		}

		public @NonNull CtBehavior getMethod(final @NonNull ClassPool classPool) throws NotFoundException {
			CtClass ctClass = classPool.get(className);
			return "<init>".equals(methodName) ? ctClass.getConstructor(methodSignature)
					: ctClass.getMethod(methodName, methodSignature);
		}
	}

	@Delegate
	private final @NonNull TaskKey arguments;

	private @NonNull Set<PaintingTask> dependants = emptySet();

	@Getter
	@Setter
	private @NonNull Set<PaintingTask> dependencies = emptySet();

	@Delegate
	@Getter
	@Setter
	private Result result;

	public PaintingTask(final @NonNull CtBehavior ctMethod) {
		this.arguments = new PaintingTask.TaskKey(ctMethod);
	}

	public PaintingTask(final @NonNull PaintingTask.TaskKey key) {
		this.arguments = key;
	}

	public synchronized void addDependant(PaintingTask node) {
		if (this.dependants == Collections.<PaintingTask>emptySet()) {
			this.dependants = newSetFromMap(new WeakHashMap<>());
		}
		this.dependants.add(node);
	}

	public synchronized Collection<PaintingTask> getDependants() {
		if (this.dependants.isEmpty()) {
			return emptySet();
		}

		return new ArrayList<PaintingTask>(this.dependants);
	}

	public synchronized void removeDependant(@NonNull PaintingTask task) {
		this.dependants.remove(task);
	}

}
