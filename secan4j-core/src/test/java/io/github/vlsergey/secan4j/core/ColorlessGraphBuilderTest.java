package io.github.vlsergey.secan4j.core;

import java.util.Arrays;
import java.util.function.Function;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.github.vlsergey.secan4j.core.springwebmvc.BadControllerExample;
import io.github.vlsergey.secan4j.core.springwebmvc.SimpleMethods;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.NotFoundException;

class ColorlessGraphBuilderTest {

	private static Stream<Arguments> provideMethods() throws Exception {
		final ClassPool classPool = ClassPool.getDefault();

		final Function<Class<?>, CtClass> toCtClass = cls -> {
			try {
				return classPool.getCtClass(cls.getName());
			} catch (NotFoundException exc) {
				throw new RuntimeException(exc);
			}
		};

		return Stream.of(BadControllerExample.class, SimpleMethods.class) //
				.map(toCtClass) //
				.flatMap(ctClass -> Arrays.stream(ctClass.getMethods())
						.map(ctMethod -> Arguments.of(ctClass, ctMethod)));

	}

	@ParameterizedTest
	@MethodSource("provideMethods")
	void testBuildGraph(CtClass ctClass, CtMethod ctMethod) throws Exception {
		new ColorlessGraphBuilder().buildGraph(ctClass, ctMethod);
	}

}
