package io.github.vlsergey.secan4j.core.colored.brushes;

import java.util.Map;

import io.github.vlsergey.secan4j.core.colored.ColoredObject;
import io.github.vlsergey.secan4j.core.colorless.BlockDataGraph;
import io.github.vlsergey.secan4j.core.colorless.DataNode;
import lombok.NonNull;

public interface ColorPaintBrush {

	@NonNull
	Map<DataNode, ColoredObject> doTouch(final @NonNull BlockDataGraph colorlessGraph,
			final @NonNull Map<DataNode, ColoredObject> oldColors);

}
