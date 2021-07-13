package io.github.vlsergey.secan4j.core.colored.brushes;

import static java.util.Collections.singletonMap;

import java.util.Arrays;
import java.util.Map;
import java.util.function.BiConsumer;

import io.github.vlsergey.secan4j.core.colored.ColorType;
import io.github.vlsergey.secan4j.core.colored.ColoredObject;
import io.github.vlsergey.secan4j.core.colored.Confidence;
import io.github.vlsergey.secan4j.core.colored.PaintedColor;
import io.github.vlsergey.secan4j.core.colored.TraceItem;
import io.github.vlsergey.secan4j.core.colorless.BlockDataGraph;
import io.github.vlsergey.secan4j.core.colorless.DataNode;
import io.github.vlsergey.secan4j.core.colorless.SourceCodePosition;
import javassist.bytecode.Opcode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NonNull;

@AllArgsConstructor
public class InvokeDynamicBrush implements ColorPaintBrush {

	@AllArgsConstructor
	@Data
	private static final class InvokeDynamicTraceItem implements TraceItem {

		private final @NonNull TraceItem previous;

		@Getter
		private final @NonNull SourceCodePosition sourceCodePosition;

		@Override
		public Map<String, ?> describe() {
			return singletonMap("message", "Result of invokeDynamic operation");
		}

		@Override
		public TraceItem findPrevious() {
			return previous;
		}
	}

	@Override
	public void doTouch(final @NonNull BlockDataGraph colorlessGraph,
			final @NonNull Map<DataNode, ColoredObject> oldColors,
			final @NonNull BiConsumer<DataNode, ColoredObject> colorApplier) {
		if (oldColors.isEmpty()) {
			return;
		}

		Arrays.stream(colorlessGraph.getAllNodes()).filter(dn -> dn.getOperation() == Opcode.INVOKEDYNAMIC)
				.forEach(node -> {
					for (DataNode inputNode : node.getInputs()) {
						ColoredObject oldColor = oldColors.get(inputNode);
						if (oldColor == null || oldColor.getColor().getType() != ColorType.SourceData
								|| oldColor.getColor().getConfidence().getValue() < Confidence.ASSUMED.getValue()) {
							continue;
						}

						final InvokeDynamicTraceItem traceItem = new InvokeDynamicTraceItem(
								oldColor.getColor().getSrc(), node.getSourceCodePosition());
						final ColoredObject newColor = ColoredObject.forRootOnly(node.getType().getCtClass(),
								new PaintedColor(Confidence.ASSUMED, traceItem, ColorType.SourceData));

						colorApplier.accept(node, newColor);
					}
				});
	}

}
