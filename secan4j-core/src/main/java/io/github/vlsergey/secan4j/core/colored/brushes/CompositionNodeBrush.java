package io.github.vlsergey.secan4j.core.colored.brushes;

import static java.util.Collections.emptyMap;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

import io.github.vlsergey.secan4j.core.colored.ColoredObject;
import io.github.vlsergey.secan4j.core.colored.TraceItem;
import io.github.vlsergey.secan4j.core.colorless.AnyOfNode;
import io.github.vlsergey.secan4j.core.colorless.BlockDataGraph;
import io.github.vlsergey.secan4j.core.colorless.DataNode;
import lombok.AllArgsConstructor;
import lombok.NonNull;

/**
 * @see AnyOfNode
 */
@AllArgsConstructor
public class CompositionNodeBrush implements ColorPaintBrush {

	@Override
	public @NonNull Map<DataNode, ColoredObject> doTouch(@NonNull BlockDataGraph colorlessGraph,
			@NonNull Map<DataNode, ColoredObject> oldColors,
			BiConsumer<TraceItem, TraceItem> onSourceSinkIntersection) {
		if (oldColors.isEmpty()) {
			return emptyMap();
		}

		Map<DataNode, ColoredObject> newColors = new HashMap<>();

		BrushUtils.getAllNodesWithType(colorlessGraph, AnyOfNode.class).forEach(node -> {
			final ColoredObject oldColor = oldColors.get(node);
			ColoredObject newColor = oldColor;
			for (DataNode inputNode : node.getInputs()) {
				newColor = ColoredObject.merge(newColor, oldColors.get(inputNode),
						(a, b) -> onSourceSinkIntersection.accept(a.getSrc(), b.getSrc()));
			}
			if (newColor != oldColor) {
				newColors.put(node, newColor);
			}
		});

		return newColors;
	}

}
