package io.github.vlsergey.secan4j.core.colored;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import io.github.vlsergey.secan4j.core.colorless.DataNode;
import lombok.Data;
import lombok.NonNull;

/**
 * Collects new colors to be applied later and also check there is no color
 * intersection between old and new colors
 */
@Data
@NotThreadSafe
public class ColorApplier implements BiConsumer<@NonNull DataNode, @Nullable ColoredObject> {

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
			newColors.put(dataNode, merged);
			return;
		}

		final @Nullable ColoredObject oldColor = oldColors.get(dataNode);
		if (oldColor == null) {
			newColors.put(dataNode, newColor);
			return;
		}

		final @NonNull ColoredObject merged = ColoredObject.merge(oldColor, newColor, this.problemsReporter);
		if (!merged.equals(oldColor)) {
			newColors.put(dataNode, merged);
		}
	}

}
