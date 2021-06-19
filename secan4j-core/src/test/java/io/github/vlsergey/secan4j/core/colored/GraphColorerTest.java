package io.github.vlsergey.secan4j.core.colored;

import org.junit.jupiter.api.Test;

import io.github.vlsergey.secan4j.core.colorless.ColorlessGraphBuilder;
import io.github.vlsergey.secan4j.core.springwebmvc.BadControllerExample;
import io.github.vlsergey.secan4j.core.user2command.UserToCommandInjectionColorer;
import io.github.vlsergey.secan4j.data.DataProvider;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;

public class GraphColorerTest {

	private final ClassPool classPool = ClassPool.getDefault();
	private final ColorlessGraphBuilder colorlessGraphBuilder = new ColorlessGraphBuilder();
	private final DataProvider dataProvider = new DataProvider();

	@Test
	void testConcatenation() throws Exception {
		final CtClass ctClass = classPool.get(SimpleColoredMethods.class.getName());
		final CtMethod ctMethod = ctClass.getDeclaredMethod("concatenation");

		GraphColorer graphColorer = new GraphColorer(classPool, colorlessGraphBuilder,
				new UserToCommandInjectionColorer(dataProvider));

		graphColorer.color(ctClass, ctMethod, (source, sink) -> {
			System.out.println(source);
			System.out.println(sink);
		});
	}

	@Test
	void testBadControllerExample() throws Exception {
		final CtClass ctClass = classPool.get(BadControllerExample.class.getName());
		final CtMethod ctMethod = ctClass.getDeclaredMethod("sqlInjection");

		GraphColorer graphColorer = new GraphColorer(classPool, colorlessGraphBuilder,
				new UserToCommandInjectionColorer(dataProvider));

		graphColorer.color(ctClass, ctMethod, (source, sink) -> {
			System.out.println(source);
			System.out.println(sink);
		});
	}

}
