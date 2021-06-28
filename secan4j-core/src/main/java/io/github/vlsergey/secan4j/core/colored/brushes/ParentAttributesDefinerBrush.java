package io.github.vlsergey.secan4j.core.colored.brushes;

import static java.util.Collections.emptyMap;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import io.github.vlsergey.secan4j.annotations.ParentAttributesDefiner;
import io.github.vlsergey.secan4j.core.colored.ColoredObject;
import io.github.vlsergey.secan4j.core.colorless.BlockDataGraph;
import io.github.vlsergey.secan4j.core.colorless.DataNode;
import io.github.vlsergey.secan4j.core.colorless.GetFieldNode;
import io.github.vlsergey.secan4j.data.DataProvider;
import lombok.AllArgsConstructor;
import lombok.NonNull;

/**
 * @see ParentAttributesDefinerBrush
 */
@AllArgsConstructor
public class ParentAttributesDefinerBrush implements ColorPaintBrush {

	private final @NonNull DataProvider dataProvider;

	@Override
	public @NonNull Map<DataNode, ColoredObject> doTouch(@NonNull BlockDataGraph colorlessGraph,
			@NonNull Map<DataNode, ColoredObject> oldColors) {
		if (oldColors.isEmpty()) {
			return emptyMap();
		}

		Map<DataNode, ColoredObject> newColors = new HashMap<>();

		BrushUtils.getAllNodesWithType(colorlessGraph, GetFieldNode.class).forEach(getField -> {

			final @NonNull Set<Class<?>> forField = dataProvider.getForField(getField.getCtClass().getName(),
					getField.getCtField().getName(), getField.getCtField().getSignature());

			if (forField.contains(ParentAttributesDefiner.class)) {
				BrushUtils.copyColor(
						oldColors, getField, color -> ColoredObject
								.forRootOnly(getField.getObjectRef().getType().getCtClass(), color.getColor()),
						getField.getObjectRef(), newColors);
			}

			BrushUtils.copyColor(oldColors, getField.getObjectRef(),
					color -> ColoredObject.forRootOnly(getField.getType().getCtClass(), color.getColor()), getField,
					newColors);

		});

		return newColors;
	}

}
