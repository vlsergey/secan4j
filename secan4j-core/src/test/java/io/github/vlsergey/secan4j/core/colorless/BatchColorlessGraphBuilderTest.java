package io.github.vlsergey.secan4j.core.colorless;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Arrays;
import java.util.function.Function;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.github.vlsergey.secan4j.core.springwebmvc.BadControllerExample;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.NotFoundException;

class BatchColorlessGraphBuilderTest {

	private static final ClassPool classPool = ClassPool.getDefault();

	private static Stream<Arguments> provideMethods() throws Exception {

		final Function<Class<?>, CtClass> toCtClass = cls -> {
			try {
				return classPool.getCtClass(cls.getName());
			} catch (NotFoundException exc) {
				throw new RuntimeException(exc);
			}
		};

		return Stream
				.of(BadControllerExample.class, PrivateMethodInvoke.Foo.class, SimpleMethods.class, StringBuilder.class) //
				.map(toCtClass) //
				.flatMap(ctClass -> Arrays.stream(ctClass.getMethods())
						.map(ctMethod -> Arguments.of(ctClass, ctMethod)));

	}

	@ParameterizedTest
	@MethodSource("provideMethods")
	void testBuildGraph(CtClass ctClass, CtMethod ctMethod) throws Exception {
		final BlockDataGraph graph = new ColorlessMethodGraphBuilder(classPool, ctClass, ctMethod).buildGraph().orElse(null);
		assertNotNull((graph == null) == ctMethod.isEmpty());
	}

}
