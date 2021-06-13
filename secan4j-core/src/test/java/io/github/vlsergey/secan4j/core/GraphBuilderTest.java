package io.github.vlsergey.secan4j.core;

import java.util.Arrays;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.github.vlsergey.secan4j.core.springwebmvc.SimpleMethods;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;

class GraphBuilderTest {

	private static Stream<Arguments> provideMethods() throws Exception {
		final ClassPool classPool = ClassPool.getDefault();
		final CtClass ctClass = classPool.getCtClass(SimpleMethods.class.getName());

		return Arrays.stream(ctClass.getMethods()).map(ctMethod -> Arguments.of(ctClass, ctMethod));
	}

	@ParameterizedTest
	@MethodSource("provideMethods")
	void testBuildGraph(CtClass ctClass, CtMethod ctMethod) throws Exception {
		new GraphBuilder().buildGraph(ctClass, ctMethod);
	}

}
