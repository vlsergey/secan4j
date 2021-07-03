package io.github.vlsergey.secan4j.core.colored;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import io.github.vlsergey.secan4j.core.colorless.DataNode;
import lombok.Data;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Collects new colors to be applied later and also check there is no color
 * intersection between old and new colors
 */
@Data
@NotThreadSafe
@Slf4j
public class ColorApplier implements BiConsumer<DataNode, ColoredObject> {

	private final @NonNull Map<DataNode, ColoredObject> newColors = new HashMap<>(1);
	private final @NonNull Map<DataNode, ColoredObject> oldColors;
	private final @NonNull BiConsumer<PaintedColor, PaintedColor> problemsReporter;

	public ColorApplier(final @NonNull Map<DataNode, ColoredObject> oldColors,
			final @NonNull BiConsumer<TraceItem, TraceItem> onSourceSinkIntersection) {
		this.oldColors = oldColors;
		this.problemsReporter = (a, b) -> onSourceSinkIntersection.accept(a.getSrc(), b.getSrc());
	}

	@Override
	public void accept(final @NonNull DataNode dataNode, final @Nullable ColoredObject newColor) {
		if (newColor == null) {
			return;
		}

		final @Nullable ColoredObject oldNewColor = newColors.get(dataNode);
		if (oldNewColor != null) {
			final @NonNull ColoredObject merged = ColoredObject.merge(oldNewColor, newColor, this.problemsReporter);
			log.debug("Merged color with type {} will be applied to {}, as merged from 2:\n* {}\n* {}",
					merged.getColor().getType(), dataNode, oldNewColor, newColor);
			newColors.put(dataNode, merged);
			return;
		}

		final @Nullable ColoredObject oldColor = oldColors.get(dataNode);
		if (oldColor == null) {
			log.debug("New color with type {} will be applied to {}: ", newColor.getColor().getType(), dataNode,
					newColor);
			newColors.put(dataNode, newColor);
			return;
		}

		final @NonNull ColoredObject merged = ColoredObject.merge(oldColor, newColor, this.problemsReporter);
		if (!merged.equals(oldColor)) {
			log.debug("Merged color with type {} will be applied to {}, as merged from 2:\n* {}\n* {}",
					merged.getColor().getType(), dataNode, oldColor, newColor);
			newColors.put(dataNode, merged);
		}
	}

}
