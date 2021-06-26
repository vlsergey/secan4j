package io.github.vlsergey.secan4j.core.colored;

import static java.util.Collections.emptyMap;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.vlsergey.secan4j.core.colorless.ColorlessGraphBuilder;
import io.github.vlsergey.secan4j.core.colorless.Invocation;
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

		List<Invocation> checkedInvokations = new ArrayList<>();

		graphColorer.color(ctClass, ctMethod, new ColoredObject[] { null, null, null }, new ColoredObject[] { null },
				(inv, a, b) -> {
					checkedInvokations.add(inv);
					return emptyMap();
				}, (source, sink) -> {
					fail("We din't expect intersection to be found so soon");
				});

		// different in different JDKs
		assertTrue(checkedInvokations.size() >= 1, "Invokations count: " + checkedInvokations.size());
	}

	@Test
	void testBadControllerExample() throws Exception {
		final CtClass ctClass = classPool.get(BadControllerExample.class.getName());
		final CtMethod ctMethod = ctClass.getDeclaredMethod("sqlInjection");

		GraphColorer graphColorer = new GraphColorer(classPool, colorlessGraphBuilder,
				new UserToCommandInjectionColorer(dataProvider));

		List<Invocation> checkedInvokations = new ArrayList<>();

		graphColorer.color(ctClass, ctMethod, new ColoredObject[] { null, null, null }, new ColoredObject[] { null },
				(inv, a, b) -> {
					checkedInvokations.add(inv);
					return emptyMap();
				}, (source, sink) -> {
					fail("We din't expect intersection to be found so soon");
				});

		// different in different JDKs
		assertTrue(checkedInvokations.size() >= 8, "Invokations count: " + checkedInvokations.size());
	}

}
