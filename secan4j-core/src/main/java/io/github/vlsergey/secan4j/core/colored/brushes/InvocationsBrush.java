package io.github.vlsergey.secan4j.core.colored.brushes;

import java.util.Arrays;
import java.util.Map;
import java.util.function.BiConsumer;

import io.github.vlsergey.secan4j.core.colored.ColoredObject;
import io.github.vlsergey.secan4j.core.colorless.BlockDataGraph;
import io.github.vlsergey.secan4j.core.colorless.DataNode;
import io.github.vlsergey.secan4j.core.colorless.Invocation;
import io.github.vlsergey.secan4j.core.session.PaintingSession;
import lombok.AllArgsConstructor;
import lombok.NonNull;

@AllArgsConstructor
public class InvocationsBrush implements ColorPaintBrush {

	private final PaintingSession paintingSession;

	@Override
	public void doTouch(final @NonNull BlockDataGraph colorlessGraph,
			final @NonNull Map<DataNode, ColoredObject> oldColors,
			final @NonNull BiConsumer<DataNode, ColoredObject> colorApplier) {

		for (Invocation invocation : colorlessGraph.getInvokations()) {
			final @NonNull ColoredObject[] args = Arrays.stream(invocation.getParameters()).map(oldColors::get)
					.toArray(ColoredObject[]::new);
			final @NonNull ColoredObject[] results = Arrays.stream(invocation.getResults()).map(oldColors::get)
					.toArray(ColoredObject[]::new);

			paintingSession.getOrQueueSubcall(invocation, args, results).forEach(colorApplier);
		}
	}

}
