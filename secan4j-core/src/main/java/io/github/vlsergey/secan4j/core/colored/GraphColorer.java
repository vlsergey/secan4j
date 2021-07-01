package io.github.vlsergey.secan4j.core.colored;

import static java.util.Collections.unmodifiableMap;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import io.github.vlsergey.secan4j.core.colored.brushes.ColorPaintBrush;
import io.github.vlsergey.secan4j.core.colorless.BlockDataGraph;
import io.github.vlsergey.secan4j.core.colorless.ColorlessGraphBuilder;
import io.github.vlsergey.secan4j.core.colorless.DataNode;
import io.github.vlsergey.secan4j.core.colorless.Invocation;
import io.github.vlsergey.secan4j.core.colorless.MethodParameterNode;
import io.github.vlsergey.secan4j.data.DataProvider;
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

	private final @NonNull List<ColorPaintBrush> brushes;

	private final @NonNull ClassPool classPool;

	private final @NonNull ColorlessGraphBuilder colorlessGraphBuilder;

	private final @NonNull ColorProvider colorProvider;

	private final @NonNull DataProvider dataProvider;

	private final @NonNull Cache<Invocation, CtClass> invClassCache = CacheBuilder.newBuilder().maximumSize(1 << 10)
			.build();

	private final @NonNull Cache<Invocation, CtBehavior> invMethodsCache = CacheBuilder.newBuilder()
			.maximumSize(1 << 10).build();

	private final @NonNull Cache<Invocation, BlockDataGraph> invGraphCache = CacheBuilder.newBuilder()
			.maximumSize(1 << 10).build();

	@Data
	private static final class InitialColoredMethodGraph {
		private final BlockDataGraph colorlessGraph;
		private final Map<DataNode, ColoredObject> initialColors;
		private final DataNode[] methodParams;
		private final DataNode[] methodReturns;
	}

	@SneakyThrows
	private @NonNull Optional<InitialColoredMethodGraph> buildInitialColoredMethodGraph(final @NonNull CtClass ctClass,
			final @NonNull CtBehavior ctMethod) {
		final @NonNull Optional<BlockDataGraph> opColorlessGraph = colorlessGraphBuilder.buildGraph(ctClass, ctMethod);
		if (opColorlessGraph.isEmpty()) {
			return Optional.empty();
		}
		final @NonNull BlockDataGraph colorlessGraph = opColorlessGraph.get();

		final Map<DataNode, ColoredObject> colors = new HashMap<>();
		for (final @NonNull DataNode dataNode : colorlessGraph.getAllNodes()) {
			if (dataNode instanceof MethodParameterNode) {
				final @NonNull MethodParameterNode typed = ((MethodParameterNode) dataNode);
				final @NonNull Optional<ColoredObject> opColor = colorProvider.getImplicitColor(typed.getCtClass(),
						typed.getCtMethod(), typed.getParameterIndex());
				opColor.ifPresent(color -> {
					if (log.isDebugEnabled()) {
						log.debug("Implicit color " + color + " assigned to " + typed);
					}
					colors.put(typed, color);
				});
			}
		}

		for (final @NonNull Invocation invocation : colorlessGraph.getInvokations()) {
			final String className = invocation.getClassName();
			if (className == null)
				continue;

			CtClass invTargetClass;
			try {
				invTargetClass = classPool.get(className);
			} catch (NotFoundException exc) {
				log.warn("Invokation class not found: " + className);
				continue;
			}
			invClassCache.put(invocation, invTargetClass);

			// TODO: down to child classes if only one present

			final CtBehavior invTargetMethod;
			try {
				invTargetMethod = invocation.getMethodName().equals("<init>")
						? invTargetClass.getConstructor(invocation.getMethodSignature())
						: invTargetClass.getMethod(invocation.getMethodName(), invocation.getMethodSignature());

				for (int i = 0; i < invTargetMethod.getParameterTypes().length; i++) {
					final DataNode dataNode = invocation.getParameters()[i];
					final @NonNull Optional<ColoredObject> opColor = colorProvider.getImplicitColor(invTargetClass,
							invTargetMethod, i);
					opColor.ifPresent(color -> {
						if (log.isDebugEnabled()) {
							log.debug("Implicit color " + color + " assigned to " + dataNode);
						}
						colors.put(dataNode, color);
					});
				}

			} catch (NotFoundException exc) {
				log.warn("Invokation method not found: " + className + "." + invocation.getMethodName() + ": '"
						+ invocation.getMethodSignature() + "'", exc);
				continue;
			}
			invMethodsCache.put(invocation, invTargetMethod);

		}

		return Optional.of(new InitialColoredMethodGraph(colorlessGraph, unmodifiableMap(colors),
				colorlessGraph.getMethodParamNodes(), colorlessGraph.getMethodReturnNodes()));
	}

	public interface invocationCallback {
		@NonNull
		Map<DataNode, ColoredObject> onInvokation(final @NonNull Invocation invocation,
				final @NonNull ColoredObject[] ins, final @NonNull ColoredObject[] outs);
	}

	@SneakyThrows
	public @NonNull Optional<ColoredObject[][]> color(final @NonNull CtClass ctClass,
			final @NonNull CtBehavior ctMethod, final ColoredObject[] ins, final ColoredObject[] outs,
			final @NonNull invocationCallback invocationCallback,
			final @NonNull BiConsumer<TraceItem, TraceItem> onSourceSinkIntersection) {

		final @NonNull Optional<InitialColoredMethodGraph> opInitial = buildInitialColoredMethodGraph(ctClass,
				ctMethod);
		if (opInitial.isEmpty()) {
			return Optional.empty();
		}
		final @NonNull InitialColoredMethodGraph initial = opInitial.get();

		final BlockDataGraph colorlessGraph = initial.getColorlessGraph();
		final Map<DataNode, ColoredObject> colors = new HashMap<>(initial.getInitialColors());

		updateInsOutsColors(ins, initial.getMethodParams(), colors);
		updateInsOutsColors(outs, initial.getMethodReturns(), colors);

		colorImpl(colorlessGraph, colors, invocationCallback, onSourceSinkIntersection);

		final ColoredObject[] newIns = Arrays.stream(initial.getMethodParams()).map(colors::get)
				.toArray(ColoredObject[]::new);
		final ColoredObject[] newOuts = Arrays.stream(initial.getMethodReturns()).map(colors::get)
				.toArray(ColoredObject[]::new);
		return Optional.of(new ColoredObject[][] { newIns, newOuts });
	}

	private void updateInsOutsColors(final ColoredObject[] sourceOfNewColors, final DataNode[] whatToUpdate,
			final @NonNull Map<DataNode, ColoredObject> colors) {
		if (sourceOfNewColors == null) {
			return;
		}

		assert sourceOfNewColors.length == whatToUpdate.length;

		for (int i = 0; i < whatToUpdate.length; i++) {
			if (sourceOfNewColors[i] == null) {
				continue;
			}
			DataNode node = whatToUpdate[i];
			ColoredObject existed = colors.get(node);
			if (existed == null) {
				colors.put(node, sourceOfNewColors[i]);
				continue;
			}

			ColoredObject toStore = ColoredObject.merge(existed, sourceOfNewColors[i], (a, b) -> {
				throw new RuntimeException("Intersection!");
			});
			if (toStore != existed) {
				colors.put(node, toStore);
			}
		}
	}

	@SneakyThrows
	private void colorImpl(final @NonNull BlockDataGraph colorlessGraph,
			final @NonNull Map<DataNode, ColoredObject> colors, final @NonNull invocationCallback invocationCallback,
			final @NonNull BiConsumer<TraceItem, TraceItem> onSourceSinkIntersection) {
		// initial colors are assigned, now time to color nodes...
		boolean hasChanges = true;
		while (hasChanges) {
			// TODO: optimize by checking only changed nodes ?
			final Map<DataNode, ColoredObject> newColors = new HashMap<>();

			final Map<DataNode, ColoredObject> oldColors = unmodifiableMap(colors);
			for (ColorPaintBrush brush : this.brushes) {
				Map<DataNode, ColoredObject> afterTouch = brush.doTouch(colorlessGraph, oldColors,
						onSourceSinkIntersection);
				afterTouch.forEach((node, newColor) -> {
					ColoredObject prev = colors.get(node);
					if (prev == null || !prev.equals(newColor)) {
						newColors.put(node, newColor);
					}
				});
			}

			for (Invocation invocation : colorlessGraph.getInvokations()) {
				final @NonNull ColoredObject[] args = Arrays.stream(invocation.getParameters()).map(colors::get)
						.toArray(ColoredObject[]::new);
				final @NonNull ColoredObject[] results = Arrays.stream(invocation.getResults()).map(colors::get)
						.toArray(ColoredObject[]::new);

				final @NonNull Map<DataNode, ColoredObject> onInvokation = invocationCallback.onInvokation(invocation,
						args, results);
				onInvokation.forEach((node, newColor) -> {
					ColoredObject prev = colors.get(node);
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
