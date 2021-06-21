package io.github.vlsergey.secan4j.core.colored;

import static java.util.Collections.unmodifiableMap;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import io.github.vlsergey.secan4j.core.colorless.BlockDataGraph;
import io.github.vlsergey.secan4j.core.colorless.ColorlessGraphBuilder;
import io.github.vlsergey.secan4j.core.colorless.DataNode;
import io.github.vlsergey.secan4j.core.colorless.Invocation;
import io.github.vlsergey.secan4j.core.colorless.MethodParameterNode;
import javassist.ClassPool;
import javassist.CtBehavior;
import javassist.CtClass;
import javassist.NotFoundException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor
@Slf4j
public class GraphColorer {

	private final @NonNull ClassPool classPool;

	private final @NonNull ColorlessGraphBuilder colorlessGraphBuilder;

	private final @NonNull ColorProvider colorProvider;

	private final @NonNull Cache<Invocation, CtClass> invClassCache = CacheBuilder.newBuilder().maximumSize(1 << 10)
			.build();

	private final @NonNull Cache<Invocation, CtBehavior> invMethodsCache = CacheBuilder.newBuilder()
			.maximumSize(1 << 10).build();

	private final @NonNull Cache<Invocation, BlockDataGraph> invGraphCache = CacheBuilder.newBuilder()
			.maximumSize(1 << 10).build();

	@Data
	private static final class InitialColoredMethodGraph {
		private final BlockDataGraph colorlessGraph;
		private final Map<DataNode, PathAndColor> initialColors;
		private final DataNode[] methodParams;
		private final DataNode[] methodReturns;
	}

	private InitialColoredMethodGraph buildInitialColoredMethodGraph(final @NonNull CtClass ctClass,
			final @NonNull CtBehavior ctMethod) {
		final BlockDataGraph colorlessGraph = colorlessGraphBuilder.buildGraph(ctClass, ctMethod);

		final Map<DataNode, PathAndColor> colors = new HashMap<>();
		for (final @NonNull DataNode dataNode : colorlessGraph.getAllNodes()) {
			if (dataNode instanceof MethodParameterNode) {
				final @NonNull MethodParameterNode typed = ((MethodParameterNode) dataNode);
				final @NonNull Optional<PathAndColor> opColor = colorProvider.getImplicitColor(typed.getCtClass(),
						typed.getCtMethod(), typed.getParameterIndex());
				opColor.ifPresent(color -> {
					if (log.isDebugEnabled()) {
						log.debug("Implicit color " + color + " assigned to " + typed);
					}
					colors.put(typed, color);
				});
			}
		}

		for (final @NonNull Invocation invokation : colorlessGraph.getInvokations()) {
			final String className = invokation.getClassName();
			if (className == null)
				continue;

			CtClass invTargetClass;
			try {
				invTargetClass = classPool.get(className);
			} catch (NotFoundException exc) {
				log.warn("Invokation class not found: " + className);
				continue;
			}
			invClassCache.put(invokation, invTargetClass);

			// TODO: down to child classes if only one present

			final CtBehavior invTargetMethod;
			try {
				invTargetMethod = invokation.getMethodName().equals("<init>")
						? invTargetClass.getConstructor(invokation.getMethodSignature())
						: invTargetClass.getMethod(invokation.getMethodName(), invokation.getMethodSignature());

				for (int i = 0; i < invTargetMethod.getParameterTypes().length; i++) {
					final DataNode dataNode = invokation.getParameters()[i];
					final @NonNull Optional<PathAndColor> opColor = colorProvider.getImplicitColor(invTargetClass,
							invTargetMethod, i);
					opColor.ifPresent(color -> {
						if (log.isDebugEnabled()) {
							log.debug("Implicit color " + color + " assigned to " + dataNode);
						}
						colors.put(dataNode, color);
					});
				}

			} catch (NotFoundException exc) {
				log.warn("Invokation method not found: " + className + "." + invokation.getMethodName() + ": '"
						+ invokation.getMethodSignature() + "'", exc);
				continue;
			}
			invMethodsCache.put(invokation, invTargetMethod);
		}

		return new InitialColoredMethodGraph(colorlessGraph, unmodifiableMap(colors),
				colorlessGraph.getMethodParamNodes(), colorlessGraph.getMethodReturnNodes());
	}

	public interface InvocationCallback {
		@NonNull
		Map<DataNode, PathAndColor> onInvokation(final @NonNull Invocation invocation,
				final @NonNull PathAndColor[] ins, final @NonNull PathAndColor[] outs);
	}

	@SneakyThrows
	public PathAndColor[][] color(final @NonNull CtClass ctClass, final @NonNull CtBehavior ctMethod,
			final PathAndColor[] ins, final PathAndColor[] outs, final @NonNull InvocationCallback invocationCallback,
			final @NonNull BiConsumer<TraceItem, TraceItem> onSourceSinkIntersection) {

		final InitialColoredMethodGraph initial = buildInitialColoredMethodGraph(ctClass, ctMethod);

		final BlockDataGraph colorlessGraph = initial.getColorlessGraph();
		final Map<DataNode, PathAndColor> colors = new HashMap<>(initial.getInitialColors());

		updateInsOutsColors(ins, initial.getMethodParams(), colors);
		updateInsOutsColors(outs, initial.getMethodReturns(), colors);

		colorImpl(colorlessGraph, colors, invocationCallback, onSourceSinkIntersection);

		final PathAndColor[] newIns = Arrays.stream(initial.getMethodParams()).map(colors::get)
				.toArray(PathAndColor[]::new);
		final PathAndColor[] newOuts = Arrays.stream(initial.getMethodReturns()).map(colors::get)
				.toArray(PathAndColor[]::new);
		return new PathAndColor[][] { newIns, newOuts };
	}

	private void updateInsOutsColors(final PathAndColor[] sourceOfNewColors, final DataNode[] whatToUpdate,
			final @NonNull Map<DataNode, PathAndColor> colors) {
		if (sourceOfNewColors == null) {
			return;
		}

		assert sourceOfNewColors.length == whatToUpdate.length;

		for (int i = 0; i < whatToUpdate.length; i++) {
			if (sourceOfNewColors[i] == null) {
				continue;
			}
			DataNode node = whatToUpdate[i];
			PathAndColor existed = colors.get(node);
			if (existed == null) {
				colors.put(node, sourceOfNewColors[i]);
				continue;
			}

			PathAndColor toStore = PathAndColor.merge(existed, sourceOfNewColors[i], (a, b) -> {
				throw new RuntimeException("Intersection!");
			});
			if (toStore != existed) {
				colors.put(node, toStore);
			}
		}
	}

	private void colorImpl(final @NonNull BlockDataGraph colorlessGraph,
			final @NonNull Map<DataNode, PathAndColor> colors, final @NonNull InvocationCallback invocationCallback,
			final @NonNull BiConsumer<TraceItem, TraceItem> onSourceSinkIntersection) {
		// initial colors are assigned, now time to color nodes...
		boolean hasChanges = true;
		while (hasChanges) {
			// TODO: optimize by checking only changed nodes
			final Map<DataNode, PathAndColor> newColors = new HashMap<>();

			for (Invocation invocation : colorlessGraph.getInvokations()) {
				final @NonNull PathAndColor[] args = Arrays.stream(invocation.getParameters()).map(colors::get)
						.toArray(PathAndColor[]::new);
				final @NonNull PathAndColor[] results = new PathAndColor[] { colors.get(invocation.getResult()) };

				invocationCallback.onInvokation(invocation, args, results).forEach((node, newColor) -> {
					PathAndColor prev = colors.get(node);
					if (prev == null || !prev.equals(newColor)) {
						newColors.put(node, newColor);
					}
				});
			}

			hasChanges = !newColors.isEmpty();
			colors.putAll(newColors);
		}
	}

}
