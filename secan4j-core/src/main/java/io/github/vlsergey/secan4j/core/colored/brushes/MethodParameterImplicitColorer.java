package io.github.vlsergey.secan4j.core.colored.brushes;

import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;

import io.github.vlsergey.secan4j.core.colored.ColorProvider;
import io.github.vlsergey.secan4j.core.colored.ColoredObject;
import io.github.vlsergey.secan4j.core.colorless.BlockDataGraph;
import io.github.vlsergey.secan4j.core.colorless.DataNode;
import io.github.vlsergey.secan4j.core.colorless.MethodParameterNode;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor
@Slf4j
public class MethodParameterImplicitColorer implements ColorPaintBrush {

	private final @NonNull ColorProvider colorProvider;

	@Override
	public @NonNull void doTouch(final @NonNull BlockDataGraph colorlessGraph,
			final @NonNull Map<DataNode, ColoredObject> oldColors,
			final @NonNull BiConsumer<DataNode, ColoredObject> onTouch) {

		BrushUtils.getAllNodesWithType(colorlessGraph, MethodParameterNode.class).forEach(dataNode -> {
			final @NonNull Optional<ColoredObject> opColor = colorProvider.getImplicitColor(dataNode.getCtClass(),
					dataNode.getCtMethod(), dataNode.getParameterIndex());
			opColor.ifPresent(color -> {
				if (log.isDebugEnabled()) {
					log.debug("Implicit color " + color + " assigned to " + dataNode);
				}
				onTouch.accept(dataNode, color);
			});
		});

	}

}
