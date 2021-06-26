package io.github.vlsergey.secan4j.core.user2command;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.google.common.reflect.TypeToken;

import io.github.vlsergey.secan4j.annotations.Command;
import io.github.vlsergey.secan4j.annotations.UserProvided;
import io.github.vlsergey.secan4j.core.colored.ColorProvider;
import io.github.vlsergey.secan4j.core.colored.ColoredObject;
import io.github.vlsergey.secan4j.core.colored.Confidence;
import io.github.vlsergey.secan4j.core.colored.TraceItem;
import io.github.vlsergey.secan4j.data.DataProvider;
import io.github.vlsergey.secan4j.data.SecanData;
import javassist.CtBehavior;
import javassist.CtClass;
import javassist.bytecode.SignatureAttribute;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.SneakyThrows;

@AllArgsConstructor
public class UserToCommandInjectionColorer implements ColorProvider {

	private final @NonNull DataProvider dataProvider;

	@AllArgsConstructor
	private static final class MethodParameterTraceItem implements TraceItem {

		private final CtClass ctClass;
		private final CtBehavior ctMethod;
		private final int parameterIndex;
		private final String prefixOfMessage;

		@Override
		public TraceItem findPrevious() {
			return null;
		}

		@Override
		public Map<String, ?> describe() {
			final LinkedHashMap<String, Object> description = new LinkedHashMap<>();
			description.put("type", "MethodParameterAnnotations");

			description.put("className", ctClass.getName());
			description.put("methodName", ctMethod.getName());
			description.put("methodLongName", ctMethod.getLongName());
			description.put("methodSignature", ctMethod.getSignature());
			description.put("parameterIndex", parameterIndex);

			description.put("message", prefixOfMessage + " argument #" + parameterIndex + " of method '"
					+ ctMethod.getName() + "' of class " + ctClass.getName());
			return description;
		}

		@Override
		public String toString() {
			return "MethodParameterTraceItem [" + (String) describe().get("message") + "]";
		}

	}

	@Override
	@SneakyThrows
	public @NonNull Optional<ColoredObject> getImplicitColor(CtClass ctClass, CtBehavior ctMethod, int parameterIndex) {
		final CtClass parameterType = ctMethod.getParameterTypes()[parameterIndex];

		for (Object ann : ctMethod.getParameterAnnotations()[parameterIndex]) {
			if (ann instanceof Command) {
				final TraceItem src = new MethodParameterTraceItem(ctClass, ctMethod, parameterIndex,
						"Annotation @Command on");

				return Optional.of(ColoredObject.sinkOnRoot(src, parameterType, Confidence.EXPLICITLY));
			} else if (ann instanceof UserProvided) {
				final TraceItem src = new MethodParameterTraceItem(ctClass, ctMethod, parameterIndex,
						"Annotation @UserProvided on");

				return Optional.of(ColoredObject.sourceOnRoot(src, parameterType, Confidence.EXPLICITLY));
			}

			// check if this annotation is configured to be source or sink
			for (TypeToken<?> cls : TypeToken.of(ann.getClass()).getTypes().interfaces()) {
				final Class<?> annClass = cls.getRawType();
				final String fqcn = annClass.getName();
				final Set<Class<?>> forAnnotation = dataProvider.getDataForClass(fqcn).getForAnnotation(fqcn);
				if (forAnnotation.contains(Command.class)) {
					final TraceItem src = new MethodParameterTraceItem(ctClass, ctMethod, parameterIndex,
							"Annotation @" + annClass.getSimpleName() + " configured as @Command on");
					return Optional.of(ColoredObject.sinkOnRoot(src, parameterType, Confidence.CONFIGURATION));
				} else if (forAnnotation.contains(UserProvided.class)) {
					final TraceItem src = new MethodParameterTraceItem(ctClass, ctMethod, parameterIndex,
							"Annotation @" + annClass.getSimpleName() + " configured as @UserProvided on");
					return Optional.of(ColoredObject.sourceOnRoot(src, parameterType, Confidence.CONFIGURATION));
				}
			}
		}

		final @NonNull SecanData secanData = dataProvider.getDataForClass(ctClass.getName());
		final Set<Class<?>> data = secanData.getForMethodArguments(ctClass.getName(), ctMethod.getName(),
				SignatureAttribute.toMethodSignature(ctMethod.getSignature()))[parameterIndex];
		if (!data.isEmpty()) {
			final TraceItem src = new MethodParameterTraceItem(ctClass, ctMethod, parameterIndex,
					"Configuration info for");

			if (data.contains(Command.class)) {
				return Optional.of(ColoredObject.sinkOnRoot(src, parameterType, Confidence.CONFIGURATION));
			} else if (data.contains(UserProvided.class)) {
				return Optional.of(ColoredObject.sourceOnRoot(src, parameterType, Confidence.CONFIGURATION));
			}
		}

		return Optional.empty();
	}

}
