package io.github.vlsergey.secan4j.data;

import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static java.util.stream.Collectors.toSet;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import javassist.bytecode.BadBytecode;
import javassist.bytecode.SignatureAttribute;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.SneakyThrows;

@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class SecanData {

	private interface ClassConfigNode extends SimpleConfigNode {
		default @NonNull Set<Class<?>> getAsForAnnotation() {
			return getAsForSingleElement();
		}

		default @NonNull Set<Class<?>> getForField(String fieldName, String signature) {
			return getAsForSingleElement();
		}

		default @NonNull Set<Class<?>>[] getForMethodArguments(String methodName, String signature) throws BadBytecode {
			final int paramsLength = SignatureAttribute.toMethodSignature(signature).getParameterTypes().length;

			@SuppressWarnings("unchecked")
			Set<Class<?>>[] result = new Set[paramsLength];
			Arrays.fill(result, emptySet());
			return result;
		}

		default @NonNull Set<Class<?>> getForMethodResult(String methodName, String signature) {
			return getAsForSingleElement();
		}
	}

	static class EmptyConfigNode implements ClassConfigNode, MethodOverridesConfigNode, SimpleConfigNode {

		static final EmptyConfigNode INSTANCE = new EmptyConfigNode();

		@Override
		public Set<Class<?>> getAsForSingleElement() {
			return emptySet();
		}
	}

	@AllArgsConstructor
	private static class ListData implements MethodOverridesConfigNode, ClassConfigNode, SimpleConfigNode {

		private final @NonNull List<String> data;

		@Override
		public Set<Class<?>> getAsForSingleElement() {
			return data.stream().map(SecanData::toAnnotationClass).collect(toSet());
		}

		@Override
		public @NonNull Set<Class<?>> getForMethodResult(String signature) {
			return data.stream().map(SecanData::toAnnotationClass).collect(toSet());
		}
	}

	@AllArgsConstructor
	private static class MapAsClassData implements ClassConfigNode {

		private final @NonNull Map<String, ?> data;

		@Override
		public Set<Class<?>> getAsForSingleElement() {
			return emptySet();
		}

		private MethodOverridesConfigNode getForMethod(String methodName) {
			final Object forMethod = data.get(methodName);
			if (forMethod == null) {
				return EmptyConfigNode.INSTANCE;
			}

			MethodOverridesConfigNode methodNode = toConfigNode(forMethod, StringData::new, ListData::new,
					MapAsMethodOverridersData::new,
					() -> new UnsupportedOperationException("Unsupported configuration format for method " + methodName
							+ " (" + data.getClass().getName() + ")"));
			return methodNode;
		}

		@Override
		public @NonNull Set<Class<?>> getForField(String fieldName, String signature) {
			return getForFieldImpl(fieldName, signature).getAsForSingleElement();
		}

		private SimpleConfigNode getForFieldImpl(String fieldName, String signature) {
			final Object forField = data.get(fieldName);
			if (forField == null) {
				return EmptyConfigNode.INSTANCE;
			}

			SimpleConfigNode fieldNode = toConfigNode(forField, StringData::new, ListData::new, null,
					() -> new UnsupportedOperationException("Unsupported configuration format for field " + fieldName
							+ " (" + data.getClass().getName() + ")"));
			return fieldNode;
		}

		@Override
		public @NonNull Set<Class<?>>[] getForMethodArguments(String methodName, String signature) throws BadBytecode {
			return getForMethod(methodName).getForMethodArguments(signature);
		}

		@Override
		public Set<Class<?>> getForMethodResult(String methodName, String signature) {
			return getForMethod(methodName).getForMethodResult(signature);
		}
	}

	@AllArgsConstructor
	private static class MapAsMethodOverridersData implements MethodOverridesConfigNode {

		private final @NonNull Map<String, ?> data;

		@Override
		public @NonNull Set<Class<?>> getAsForSingleElement() {
			if (data.containsKey("result")) {
				return SecanData.<SimpleConfigNode>toConfigNode(data.get("result"), StringData::new, ListData::new,
						(any) -> EmptyConfigNode.INSTANCE,
						() -> new UnsupportedOperationException(
								"Unsupported configuration format for result of method (" + data.getClass().getName()
										+ ")"))
						.getAsForSingleElement();
			}
			return emptySet();
		}

		@Override
		@SuppressWarnings("unchecked")
		public @NonNull Set<Class<?>>[] getForMethodArguments(String signature) {
			// TODO: support for overrides

			if (data.containsKey("arguments")) {
				// must be a list or a string
				List<?> list = (List<?>) data.get("arguments");
				// must be strings OR lists of strings
				return list.stream().map(x -> x == null ? null
						: SecanData
								.toConfigNode(x, StringData::new, ListData::new, (any) -> EmptyConfigNode.INSTANCE,
										() -> new UnsupportedOperationException(
												"Unsupported configuration format for result of method override "
														+ signature + " (" + data.getClass().getName() + ")"))
								.getAsForSingleElement())
						.toArray(Set[]::new);
			}

			throw new UnsupportedOperationException("Unsupported configuration format for result of method override "
					+ signature + " (" + data.getClass().getName() + ")");
		}

		@Override
		public @NonNull Set<Class<?>> getForMethodResult(String signature) {
			if (data.containsKey(signature)) {
				return SecanData
						.<SimpleConfigNode>toConfigNode(data.get(signature), StringData::new, ListData::new,
								(any) -> EmptyConfigNode.INSTANCE,
								() -> new UnsupportedOperationException(
										"Unsupported configuration format for method override with signature '"
												+ signature + "' (" + data.getClass().getName() + ")"))
						.getAsForSingleElement();
			}

			return getAsForSingleElement();
		}

	}

	/**
	 * Node for all methods with same name
	 */
	private interface MethodOverridesConfigNode extends SimpleConfigNode {

		default @NonNull Set<Class<?>>[] getForMethodArguments(String signature) throws BadBytecode {
			final int paramsLength = SignatureAttribute.toMethodSignature(signature).getParameterTypes().length;

			@SuppressWarnings("unchecked")
			Set<Class<?>>[] result = new Set[paramsLength];
			Arrays.fill(result, emptySet());
			return result;
		}

		default @NonNull Set<Class<?>> getForMethodResult(String signature) {
			return getAsForSingleElement();
		}
	}

	private interface SimpleConfigNode {

		@NonNull
		Set<Class<?>> getAsForSingleElement();

	}

	@AllArgsConstructor
	private static class StringData implements ClassConfigNode, MethodOverridesConfigNode, SimpleConfigNode {

		private final @NonNull String data;

		@Override
		public Set<Class<?>> getAsForSingleElement() {
			return singleton(toAnnotationClass(data));
		}

		@Override
		public @NonNull Set<Class<?>> getForMethodResult(String signature) {
			return singleton(toAnnotationClass(data));
		}

	}

	@SneakyThrows
	private static Class<?> toAnnotationClass(String str) {
		return Class.forName("io.github.vlsergey.secan4j.annotations." + str);
	}

	@SuppressWarnings("unchecked")
	private static <T> T toConfigNode(final @NonNull Object src, final @NonNull Function<String, T> fromString,
			final @NonNull Function<List<String>, T> fromList, final @Nullable Function<Map<String, ?>, T> fromMap,
			final @NonNull Supplier<UnsupportedOperationException> exc) {
		if (src instanceof String) {
			return fromString.apply((String) src);
		}
		if (src instanceof List) {
			return fromList.apply((List<String>) src);
		}
		if (src instanceof Map && fromMap != null) {
			return fromMap.apply((Map<String, ?>) src);
		}
		throw exc.get();
	}

	// TODO: compact
	protected final Map<String, ?> data;

	public Set<Class<?>> getForAnnotation(String annotationClassName) {
		return getForClass(annotationClassName).getAsForAnnotation();
	}

	private @NonNull ClassConfigNode getForClass(String fqcn) {
		Object currentNode = data;

		int afterPrevDot = 0;
		final int length = fqcn.length();

		for (int charIndex = 0; charIndex < length + 1; charIndex++) {
			if (charIndex == length || fqcn.charAt(charIndex) == '.') {
				final String token = fqcn.substring(afterPrevDot, charIndex);
				afterPrevDot = charIndex + 1;

				// TODO: handle case for package-wide defaults
				final Map<?, ?> asMap = (Map<?, ?>) currentNode;
				if (!asMap.containsKey(token)) {
					return EmptyConfigNode.INSTANCE;
				}
				currentNode = asMap.get(token);
			}
		}

		return toConfigNode(currentNode, StringData::new, ListData::new, MapAsClassData::new,
				() -> new UnsupportedOperationException(
						"Unsupported configuration format for " + fqcn + " (" + data.getClass().getName() + ")"));
	}

	@SneakyThrows
	public Set<Class<?>> getForField(String fqcn, String fieldName, String signature) {
		return getForClass(fqcn).getForField(fieldName, signature);
	}

	@SneakyThrows
	public Set<Class<?>>[] getForMethodArguments(String fqcn, String methodName, String signature) {
		return getForClass(fqcn).getForMethodArguments(methodName, signature);
	}

	@SneakyThrows
	public Set<Class<?>> getForMethodResult(String fqcn, String methodName, String signature) {
		return getForClass(fqcn).getForMethodResult(methodName, signature);
	}

}
