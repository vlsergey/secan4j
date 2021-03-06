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

		final BlockDataGraph graph = new ColorlessMethodGraphBuilder(classPool, ctClass, ctMethod).buildGraph().orElse(null);

		assertEquals(1, graph.getMethodParamNodes().length);
		assertEquals(Type.get(classPool.get(Object.class.getName())), graph.getMethodParamNodes()[0].type);

		assertEquals(1, graph.getMethodReturnNodes().length);
		assertTrue(graph.getMethodReturnNodes()[0] instanceof AnyOfNode);
		assertEquals(Type.get(classPool.get(String.class.getName())), graph.getMethodReturnNodes()[0].type);
		assertEquals(2, graph.getMethodReturnNodes()[0].inputs.length);
	}

	@Test
	void testSumThree() throws Exception {
		final CtClass ctClass = classPool.get(SimpleMethods.class.getName());
		final CtMethod ctMethod = ctClass.getDeclaredMethod("sumThree");

		final BlockDataGraph graph = new ColorlessMethodGraphBuilder(classPool, ctClass, ctMethod).buildGraph().orElse(null);

		assertEquals(4, graph.getMethodParamNodes().length);
		assertEquals(Type.get(ctClass), graph.getMethodParamNodes()[0].type);
		assertEquals(Type.INTEGER, graph.getMethodParamNodes()[1].type);
		assertEquals(Type.INTEGER, graph.getMethodParamNodes()[2].type);
		assertEquals(Type.INTEGER, graph.getMethodParamNodes()[3].type);

		assertEquals(1, graph.getMethodReturnNodes().length);
		assertEquals(Type.INTEGER, graph.getMethodReturnNodes()[0].type);

		final DataNode[] allNodes = graph.getAllNodes();
		assertEquals(6, allNodes.length);
	}

}
