package io.github.vlsergey.secan4j.core.colored;

import static java.util.Collections.emptyMap;
import static java.util.Collections.unmodifiableMap;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;

import io.github.vlsergey.secan4j.core.colored.brushes.ColorPaintBrush;
import io.github.vlsergey.secan4j.core.colorless.BlockDataGraph;
import io.github.vlsergey.secan4j.core.colorless.ColorlessGraphBuilder;
import io.github.vlsergey.secan4j.core.colorless.DataNode;
import javassist.CtBehavior;
import javassist.CtClass;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NonNull;
import lombok.SneakyThrows;

@AllArgsConstructor
public class GraphColorer {

	private final @NonNull List<ColorPaintBrush> brushesInitial;

	private final @NonNull List<ColorPaintBrush> brushesRepeatable;

	@Data
	private static final class InitialColoredMethodGraph {
		private final BlockDataGraph colorlessGraph;
		private final Map<DataNode, ColoredObject> initialColors;
		private final DataNode[] methodParams;
		private final DataNode[] methodReturns;
	}

	@SneakyThrows
	private @NonNull Optional<InitialColoredMethodGraph> buildInitialColoredMethodGraph(final @NonNull CtClass ctClass,
			final @NonNull CtBehavior ctMethod,
			final @NonNull BiConsumer<TraceItem, TraceItem> onSourceSinkIntersection) {
		final @NonNull Optional<BlockDataGraph> opColorlessGraph = new ColorlessGraphBuilder(ctClass.getClassPool(),
				ctClass, ctMethod).buildGraph();
		if (opColorlessGraph.isEmpty()) {
			return Optional.empty();
		}
		final @NonNull BlockDataGraph colorlessGraph = opColorlessGraph.get();

		final ColorApplier colorApplier = new ColorApplier(emptyMap(), onSourceSinkIntersection);
		brushesInitial.forEach(brush -> {
			brush.doTouch(colorlessGraph, emptyMap(), colorApplier);
		});

		return Optional.of(new InitialColoredMethodGraph(colorlessGraph, unmodifiableMap(colorApplier.getNewColors()),
				colorlessGraph.getMethodParamNodes(), colorlessGraph.getMethodReturnNodes()));
	}

	@SneakyThrows
	public @NonNull Optional<ColoredObject[][]> color(final @NonNull CtClass ctClass,
			final @NonNull CtBehavior ctMethod, final ColoredObject[] ins, final ColoredObject[] outs,
			final @NonNull BiConsumer<TraceItem, TraceItem> onSourceSinkIntersection) {

		final @NonNull Optional<InitialColoredMethodGraph> opInitial = buildInitialColoredMethodGraph(ctClass, ctMethod,
				onSourceSinkIntersection);
		if (opInitial.isEmpty()) {
			return Optional.empty();
		}
		final @NonNull InitialColoredMethodGraph initial = opInitial.get();

		final BlockDataGraph colorlessGraph = initial.getColorlessGraph();
		final Map<DataNode, ColoredObject> colors = new HashMap<>(initial.getInitialColors());

		updateInsOutsColors(ins, initial.getMethodParams(), colors);
		updateInsOutsColors(outs, initial.getMethodReturns(), colors);

		colorImpl(colorlessGraph, colors, onSourceSinkIntersection);

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
			final @NonNull Map<DataNode, ColoredObject> colors,
			final @NonNull BiConsumer<TraceItem, TraceItem> onSourceSinkIntersection) {
		// initial colors are assigned, now time to color nodes...
		boolean hasChanges = true;
		while (hasChanges) {
			// TODO: optimize by checking only changed nodes ?
			final ColorApplier colorApplier = new ColorApplier(colors, onSourceSinkIntersection);

			final Map<DataNode, ColoredObject> oldColors = unmodifiableMap(colors);
			this.brushesRepeatable.forEach(brush -> brush.doTouch(colorlessGraph, oldColors, colorApplier));

			final @NonNull Map<DataNode, ColoredObject> newColors = colorApplier.getNewColors();
			hasChanges = !newColors.isEmpty();
			colors.putAll(newColors);
		}
	}

}
