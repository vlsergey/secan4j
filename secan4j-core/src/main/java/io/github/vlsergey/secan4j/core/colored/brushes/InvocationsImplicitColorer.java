package io.github.vlsergey.secan4j.core.colored.brushes;

import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;

import io.github.vlsergey.secan4j.core.colored.ColorProvider;
import io.github.vlsergey.secan4j.core.colored.ColoredObject;
import io.github.vlsergey.secan4j.core.colorless.BlockDataGraph;
import io.github.vlsergey.secan4j.core.colorless.DataNode;
import io.github.vlsergey.secan4j.core.colorless.Invocation;
import javassist.ClassPool;
import javassist.CtBehavior;
import javassist.CtClass;
import javassist.NotFoundException;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor
@Slf4j
public class InvocationsImplicitColorer implements ColorPaintBrush {

	private final @NonNull ClassPool classPool;

	private final @NonNull ColorProvider colorProvider;

	@Override
	public void doTouch(final @NonNull BlockDataGraph colorlessGraph,
			final @NonNull Map<DataNode, ColoredObject> oldColors,
			final @NonNull BiConsumer<DataNode, ColoredObject> onTouch) {

		for (final @NonNull Invocation invocation : colorlessGraph.getInvokations()) {
			final String className = invocation.getClassName();
			if (className == null)
				continue;

			CtClass invTargetClass;
			try {
				invTargetClass = classPool.get(className);
			} catch (NotFoundException exc) {
				log.warn("Invokation class not found: " + className);
				continue;
			}

			final CtBehavior invTargetMethod;
			try {
				invTargetMethod = invocation.getMethodName().equals("<init>")
						? invTargetClass.getConstructor(invocation.getMethodSignature())
						: invTargetClass.getMethod(invocation.getMethodName(), invocation.getMethodSignature());

				boolean thisAsDataNode = !invocation.isStaticCall();

				for (int parameterIndex = 0; parameterIndex < invTargetMethod
						.getParameterTypes().length; parameterIndex++) {
					final int dataNodeIndex = (thisAsDataNode ? 1 : 0) + parameterIndex;
					final DataNode dataNode = invocation.getParameters()[dataNodeIndex];
					final @NonNull Optional<ColoredObject> opColor = colorProvider.getImplicitColor(invTargetClass,
							invTargetMethod, parameterIndex);

					opColor.ifPresent(color -> {
						if (log.isDebugEnabled()) {
							log.debug("Implicit color " + color + " assigned to " + dataNode);
						}
						onTouch.accept(dataNode, color);
					});
				}

			} catch (NotFoundException exc) {
				log.warn("Invokation method not found: " + className + "." + invocation.getMethodName() + ": '"
						+ invocation.getMethodSignature() + "'", exc);
				continue;
			}
		}
	}

}
