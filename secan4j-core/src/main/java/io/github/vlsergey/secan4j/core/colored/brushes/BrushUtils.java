package io.github.vlsergey.secan4j.core.colored.brushes;

import java.util.Arrays;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Stream;

import io.github.vlsergey.secan4j.core.colored.ColoredObject;
import io.github.vlsergey.secan4j.core.colorless.BlockDataGraph;
import io.github.vlsergey.secan4j.core.colorless.DataNode;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class BrushUtils {

	@SuppressWarnings("unchecked")
	static <T> Stream<T> getAllNodesWithType(BlockDataGraph graph, Class<T> cls) {
		return Arrays.stream(graph.getAllNodes()).filter(dn -> cls.isInstance(dn)).map(dn -> (T) dn);
	}

	static void copyColor(final @NonNull Map<DataNode, ColoredObject> oldColors, final @NonNull DataNode source,
			final @NonNull Function<ColoredObject, ColoredObject> colorTransformation, final @NonNull DataNode target,
			final @NonNull BiConsumer<DataNode, ColoredObject> colorApplier) {
		final ColoredObject sourceColor = oldColors.get(source);
		if (sourceColor == null) {
			return;
		}

		final ColoredObject oldTargetColor = oldColors.get(target);
		final ColoredObject newTargetColor = colorTransformation.apply(sourceColor);

		if (!newTargetColor.equals(oldTargetColor)) {
			log.debug("Copying color {} from {} to {} as {}", sourceColor, source, target, newTargetColor);
			colorApplier.accept(target, newTargetColor);
		}
	}

}
