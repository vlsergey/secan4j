package io.github.vlsergey.secan4j.core.colored.brushes;

import java.util.Map;
import java.util.function.BiConsumer;

import io.github.vlsergey.secan4j.core.colored.ColoredObject;
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
	public void doTouch(@NonNull BlockDataGraph colorlessGraph, @NonNull Map<DataNode, ColoredObject> oldColors,
			BiConsumer<DataNode, ColoredObject> onTouch) {
		if (oldColors.isEmpty()) {
			return;
		}

		BrushUtils.getAllNodesWithType(colorlessGraph, AnyOfNode.class).forEach(node -> {
			ColoredObject toColorWith = null;
			for (DataNode inputNode : node.getInputs()) {
				toColorWith = ColoredObject.mergeToMostDangerous(toColorWith, oldColors.get(inputNode));
			}
			if (toColorWith != null) {
				onTouch.accept(node, toColorWith);
			}
		});
	}

}
