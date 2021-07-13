package io.github.vlsergey.secan4j.core.colored.brushes;

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.BiConsumer;

import io.github.vlsergey.secan4j.annotations.CopyAttributesFrom;
import io.github.vlsergey.secan4j.annotations.CopyAttributesTo;
import io.github.vlsergey.secan4j.core.colored.ColoredObject;
import io.github.vlsergey.secan4j.core.colored.TraceItem;
import io.github.vlsergey.secan4j.core.colorless.BlockDataGraph;
import io.github.vlsergey.secan4j.core.colorless.DataNode;
import io.github.vlsergey.secan4j.core.colorless.Invocation;
import io.github.vlsergey.secan4j.core.colorless.SourceCodePosition;
import io.github.vlsergey.secan4j.data.DataProvider;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NonNull;

/**
 * @see CopyAttributesFrom
 * @see CopyAttributesTo
 */
@AllArgsConstructor
public class CopierBrush implements ColorPaintBrush {

	@Data
	private static final class CopyTraceItem implements TraceItem {

		@Getter
		private final SourceCodePosition sourceCodePosition;
		private final TraceItem src;
		private final String message;

		private CopyTraceItem(final TraceItem src, final SourceCodePosition sourceCodePosition, final String message) {
			this.src = src;
			this.sourceCodePosition = sourceCodePosition;
			this.message = message;
		}

		@Override
		public TraceItem findPrevious() {
			return src;
		}

	}

	private final @NonNull DataProvider dataProvider;

	@Override
	public @NonNull void doTouch(@NonNull BlockDataGraph colorlessGraph,
			@NonNull Map<DataNode, ColoredObject> oldColors, BiConsumer<DataNode, ColoredObject> onTouch) {
		for (Invocation invocation : colorlessGraph.getInvokations()) {
			if (invocation.getClassName() == null || invocation.getMethodName() == null
					|| invocation.getMethodSignature() == null) {
				continue;
			}

			final Set<Class<?>> forMethodResult = dataProvider.getForMethodResult(invocation.getClassName(),
					invocation.getClassName(), invocation.getMethodSignature());
			final Set<Class<?>>[] forMethodArguments = dataProvider.getForMethodArguments(invocation.getClassName(),
					invocation.getMethodName(), invocation.getMethodSignature());

			if (forMethodResult.contains(CopyAttributesTo.class) || Arrays.stream(forMethodArguments)
					.anyMatch(s -> s != null && s.contains(CopyAttributesTo.class))) {
				// yes, we have copy-colors annotation
				List<Entry<DataNode, String>> sources = new ArrayList<>(1);
				List<Entry<DataNode, String>> targets = new ArrayList<>(1);
				if (forMethodResult.contains(CopyAttributesFrom.class)) {
					Arrays.stream(invocation.getResults()).map(dn -> new SimpleEntry<>(dn, "result"))
							.forEach(sources::add);
				}
				if (forMethodResult.contains(CopyAttributesTo.class)) {
					Arrays.stream(invocation.getResults()).map(dn -> new SimpleEntry<>(dn, "result"))
							.forEach(targets::add);
				}
				for (int i = 0; i < Math.min(invocation.getParameters().length, forMethodArguments.length); i++) {
					final Set<Class<?>> forArg = forMethodArguments[i];
					if (forArg == null || forArg.isEmpty()) {
						continue;
					}
					if (forArg.contains(CopyAttributesFrom.class)) {
						sources.add(new SimpleEntry<>(invocation.getParameters()[i], "arg" + i));
					}
					if (forArg.contains(CopyAttributesTo.class)) {
						targets.add(new SimpleEntry<>(invocation.getParameters()[i], "arg" + i));
					}
				}

				assert sources.size() == 1 : "sources.size() != 1 (NYI)";
				assert targets.size() == 1 : "targets.size() != 1 (NYI)";

				final Entry<DataNode, String> srcEntry = sources.get(0);
				final Entry<DataNode, String> dstEntry = targets.get(0);

				BrushUtils
						.copyColor(oldColors, srcEntry.getKey(),
								color -> color.withNewTraceItem(src -> new CopyTraceItem(src,
										invocation.getCallSourceCodePosition(),
										"Copy attribute from " + srcEntry.getValue() + " to " + dstEntry.getValue()
												+ " of method " + invocation.getClassName() + "."
												+ invocation.getMethodName() + "(…)")),
								dstEntry.getKey(), onTouch);
			}
		}
	}

}
