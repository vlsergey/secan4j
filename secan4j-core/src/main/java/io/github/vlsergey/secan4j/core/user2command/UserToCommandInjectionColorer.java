package io.github.vlsergey.secan4j.core.user2command;

import java.util.Optional;
import java.util.Set;

import com.google.common.reflect.TypeToken;

import io.github.vlsergey.secan4j.annotations.Command;
import io.github.vlsergey.secan4j.annotations.UserProvided;
import io.github.vlsergey.secan4j.core.colored.ColorProvider;
import io.github.vlsergey.secan4j.core.colored.ColoredObject;
import io.github.vlsergey.secan4j.core.colored.Confidence;
import io.github.vlsergey.secan4j.core.colored.TraceItem;
import io.github.vlsergey.secan4j.core.colorless.SourceCodePosition;
import io.github.vlsergey.secan4j.data.DataProvider;
import io.github.vlsergey.secan4j.data.SecanData;
import javassist.CtBehavior;
import javassist.CtClass;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.SneakyThrows;

@AllArgsConstructor
public class UserToCommandInjectionColorer implements ColorProvider {

	private final @NonNull DataProvider dataProvider;

	@Override
	@SneakyThrows
	public @NonNull Optional<ColoredObject> getImplicitColor(CtClass ctClass, CtBehavior ctMethod, int parameterIndex) {
		final CtClass parameterType = ctMethod.getParameterTypes()[parameterIndex];
		final SourceCodePosition sourceCodePosition = new SourceCodePosition(ctMethod.getDeclaringClass().getName(),
				ctMethod.getName(), ctMethod.getMethodInfo().getLineNumber(0));

		for (Object ann : ctMethod.getParameterAnnotations()[parameterIndex]) {
			if (ann instanceof Command) {
				final TraceItem src = new ImplicitMethodParameterColor(ctClass, ctMethod, parameterIndex,
						"Annotation @Command on", sourceCodePosition);

				return Optional.of(ColoredObject.sinkOnRoot(src, parameterType, Confidence.EXPLICITLY));
			} else if (ann instanceof UserProvided) {
				final TraceItem src = new ImplicitMethodParameterColor(ctClass, ctMethod, parameterIndex,
						"Annotation @UserProvided on", sourceCodePosition);

				return Optional.of(ColoredObject.sourceOnRoot(src, parameterType, Confidence.EXPLICITLY));
			}

			// check if this annotation is configured to be source or sink
			for (TypeToken<?> cls : TypeToken.of(ann.getClass()).getTypes().interfaces()) {
				final Class<?> annClass = cls.getRawType();
				final String fqcn = annClass.getName();
				final Set<Class<?>> forAnnotation = dataProvider.getDataForClass(fqcn).getForAnnotation(fqcn);
				if (forAnnotation.contains(Command.class)) {
					final TraceItem src = new ImplicitMethodParameterColor(ctClass, ctMethod, parameterIndex,
							"Annotation @" + annClass.getSimpleName() + " configured as @Command on",
							sourceCodePosition);
					return Optional.of(ColoredObject.sinkOnRoot(src, parameterType, Confidence.CONFIGURATION));
				} else if (forAnnotation.contains(UserProvided.class)) {
					final TraceItem src = new ImplicitMethodParameterColor(ctClass, ctMethod, parameterIndex,
							"Annotation @" + annClass.getSimpleName() + " configured as @UserProvided on",
							sourceCodePosition);
					return Optional.of(ColoredObject.sourceOnRoot(src, parameterType, Confidence.CONFIGURATION));
				}
			}
		}

		final @NonNull SecanData secanData = dataProvider.getDataForClass(ctClass.getName());
		final Set<Class<?>>[] forAllMethodArguments = secanData.getForMethodArguments(ctClass.getName(),
				ctMethod.getName(), ctMethod.getSignature());
		if (forAllMethodArguments != null && forAllMethodArguments.length > parameterIndex
				&& forAllMethodArguments[parameterIndex] != null && !forAllMethodArguments[parameterIndex].isEmpty()) {
			final Set<Class<?>> data = forAllMethodArguments[parameterIndex];
			final TraceItem src = new ImplicitMethodParameterColor(ctClass, ctMethod, parameterIndex,
					"Configuration info for", sourceCodePosition);

			if (data.contains(Command.class)) {
				return Optional.of(ColoredObject.sinkOnRoot(src, parameterType, Confidence.CONFIGURATION));
			} else if (data.contains(UserProvided.class)) {
				return Optional.of(ColoredObject.sourceOnRoot(src, parameterType, Confidence.CONFIGURATION));
			}
		}

		return Optional.empty();
	}

}
