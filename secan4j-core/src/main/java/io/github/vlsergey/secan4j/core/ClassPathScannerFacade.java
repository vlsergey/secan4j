package io.github.vlsergey.secan4j.core;

import java.net.URL;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;

import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;

import javassist.ClassPool;
import javassist.CtClass;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor
@Slf4j
public class ClassPathScannerFacade<T> {

	private final @NonNull String basePackage;

	private final @NonNull URL[] classPath;

	private final @NonNull ClassPool classPool;

	private final @NonNull Function<CtClass, Stream<T>> extractor;

	public Stream<T> scan() {
		final Reflections reflections = new Reflections(basePackage, classPath, new SubTypesScanner(false));

		return reflections.getAllTypes().stream().map(clsName -> {
			try {
				return classPool.get(clsName);
			} catch (Exception exc) {
				log.warn(
						"Unable to load class '" + clsName + "' to check if it has entry points to be analyzed: " + exc,
						exc);
				return null;
			}
		}).filter(Objects::nonNull).flatMap(extractor);
	}

}
