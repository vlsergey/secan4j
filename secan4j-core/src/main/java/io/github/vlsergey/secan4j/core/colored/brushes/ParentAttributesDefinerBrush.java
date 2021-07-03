package io.github.vlsergey.secan4j.core.colored.brushes;

import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

import io.github.vlsergey.secan4j.annotations.ParentAttributesDefiner;
import io.github.vlsergey.secan4j.core.colored.ColoredObject;
import io.github.vlsergey.secan4j.core.colorless.BlockDataGraph;
import io.github.vlsergey.secan4j.core.colorless.DataNode;
import io.github.vlsergey.secan4j.core.colorless.GetFieldNode;
import io.github.vlsergey.secan4j.core.colorless.PutFieldNode;
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
	public void doTouch(final @NonNull BlockDataGraph colorlessGraph,
			final @NonNull Map<DataNode, ColoredObject> oldColors,
			final @NonNull BiConsumer<DataNode, ColoredObject> onTouch) {

		BrushUtils.getAllNodesWithType(colorlessGraph, GetFieldNode.class).forEach(getField -> {

			final @NonNull Set<Class<?>> forField = dataProvider.getForField(getField.getCtClass().getName(),
					getField.getCtField().getName(), getField.getCtField().getSignature());

			if (forField.contains(ParentAttributesDefiner.class)) {
				BrushUtils.copyColor(
						oldColors, getField, color -> ColoredObject
								.forRootOnly(getField.getObjectRef().getType().getCtClass(), color.getColor()),
						getField.getObjectRef(), onTouch);
			}

			BrushUtils.copyColor(oldColors, getField.getObjectRef(),
					color -> ColoredObject.forRootOnly(getField.getType().getCtClass(), color.getColor()), getField,
					onTouch);

		});

		for (PutFieldNode putFieldNode : colorlessGraph.getPutFieldNodes()) {
			final @NonNull Set<Class<?>> forField = dataProvider.getForField(putFieldNode.getCtClass().getName(),
					putFieldNode.getCtField().getName(), putFieldNode.getCtField().getSignature());

			if (forField.contains(ParentAttributesDefiner.class)) {
				BrushUtils.copyColor(
						oldColors, putFieldNode.getValue(), color -> ColoredObject
								.forRootOnly(putFieldNode.getObjectRef().getType().getCtClass(), color.getColor()),
						putFieldNode.getObjectRef(), onTouch);

				BrushUtils.copyColor(
						oldColors, putFieldNode.getObjectRef(), color -> ColoredObject
								.forRootOnly(putFieldNode.getValue().getType().getCtClass(), color.getColor()),
						putFieldNode.getValue(), onTouch);
			}
		}
	}

}
