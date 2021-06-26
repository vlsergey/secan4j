package io.github.vlsergey.secan4j.core.colorless;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.bytecode.analysis.Type;

class ColorlessGraphBuilderTest {

	private final ClassPool classPool = ClassPool.getDefault();

	@Test
	void testStringValueOf() throws Exception {
		final CtClass ctClass = classPool.get(SimpleMethods.class.getName());
		final CtMethod ctMethod = ctClass.getDeclaredMethod("xOrNull");

		final BlockDataGraph graph = new ColorlessGraphBuilder().buildGraph(ctClass, ctMethod).orElse(null);

		assertEquals(1, graph.getMethodParamNodes().length);
		assertEquals(Type.get(classPool.get(Object.class.getName())), graph.getMethodParamNodes()[0].type);

		assertEquals(1, graph.getMethodReturnNodes().length);
		assertTrue(graph.getMethodReturnNodes()[0] instanceof AnyOfNode);
		assertEquals(Type.get(classPool.get(String.class.getName())), graph.getMethodReturnNodes()[0].type);
		assertEquals(2, graph.getMethodReturnNodes()[0].inputs.length);
	}

}
