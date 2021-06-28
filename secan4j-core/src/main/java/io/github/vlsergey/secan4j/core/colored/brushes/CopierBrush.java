package io.github.vlsergey.secan4j.core.colored.brushes;

import static java.util.Collections.emptyMap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import io.github.vlsergey.secan4j.annotations.CopyAttributesFrom;
import io.github.vlsergey.secan4j.annotations.CopyAttributesTo;
import io.github.vlsergey.secan4j.core.colored.ColoredObject;
import io.github.vlsergey.secan4j.core.colorless.BlockDataGraph;
import io.github.vlsergey.secan4j.core.colorless.DataNode;
import io.github.vlsergey.secan4j.core.colorless.Invocation;
import io.github.vlsergey.secan4j.data.DataProvider;
import lombok.AllArgsConstructor;
import lombok.NonNull;

/**
 * @see CopyAttributesFrom
 * @see CopyAttributesTo
 */
@AllArgsConstructor
public class CopierBrush implements ColorPaintBrush {

	private final @NonNull DataProvider dataProvider;

	@Override
	public @NonNull Map<DataNode, ColoredObject> doTouch(@NonNull BlockDataGraph colorlessGraph,
			@NonNull Map<DataNode, ColoredObject> oldColors) {
		if (oldColors.isEmpty()) {
			return emptyMap();
		}

		Map<DataNode, ColoredObject> newColors = new HashMap<>();

		for (Invocation invocation : colorlessGraph.getInvokations()) {
			final Set<Class<?>> forMethodResult = dataProvider.getForMethodResult(invocation.getClassName(),
					invocation.getClassName(), invocation.getMethodSignature());
			final Set<Class<?>>[] forMethodArguments = dataProvider.getForMethodArguments(invocation.getClassName(),
					invocation.getMethodName(), invocation.getMethodSignature());

			if (forMethodResult.contains(CopyAttributesTo.class) || Arrays.stream(forMethodArguments)
					.anyMatch(s -> s != null && s.contains(CopyAttributesTo.class))) {
				// yes, we have copy-colors annotation
				List<DataNode> sources = new ArrayList<>(1);
				List<DataNode> targets = new ArrayList<>(1);
				if (forMethodResult.contains(CopyAttributesFrom.class)) {
					sources.addAll(Arrays.asList(invocation.getResults()));
				}
				if (forMethodResult.contains(CopyAttributesTo.class)) {
					targets.addAll(Arrays.asList(invocation.getResults()));
				}
				for (int i = 0; i < Math.min(invocation.getParameters().length, forMethodArguments.length); i++) {
					final Set<Class<?>> forArg = forMethodArguments[i];
					if (forArg == null || forArg.isEmpty()) {
						continue;
					}
					if (forArg.contains(CopyAttributesFrom.class)) {
						sources.add(invocation.getParameters()[i]);
					}
					if (forArg.contains(CopyAttributesTo.class)) {
						targets.add(invocation.getParameters()[i]);
					}
				}

				assert sources.size() == 1 : "sources.size() != 1 (NYI)";
				assert targets.size() == 1 : "targets.size() != 1 (NYI)";

				BrushUtils.copyColor(oldColors, sources.get(0), Function.identity(), targets.get(0), newColors);
			}
		}

		return newColors;

	}

}
