package io.github.vlsergey.secan4j.core.colored;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.github.vlsergey.secan4j.core.colored.brushes.CopierBrush;
import io.github.vlsergey.secan4j.core.colorless.ColorlessGraphBuilder;
import io.github.vlsergey.secan4j.core.colorless.Invocation;
import io.github.vlsergey.secan4j.core.springwebmvc.BadControllerExample;
import io.github.vlsergey.secan4j.core.user2command.UserToCommandInjectionColorer;
import io.github.vlsergey.secan4j.data.DataProvider;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import lombok.NonNull;

public class GraphColorerTest {

	private final ClassPool classPool = ClassPool.getDefault();
	private final ColorlessGraphBuilder colorlessGraphBuilder = new ColorlessGraphBuilder();
	private final DataProvider dataProvider = new DataProvider();
	private final UserToCommandInjectionColorer userToCommand = new UserToCommandInjectionColorer(dataProvider);

	@Test
	void testArraycopy() throws Exception {
		final GraphColorer graphColorer = new GraphColorer(singletonList(new CopierBrush(dataProvider)), classPool,
				colorlessGraphBuilder, userToCommand, dataProvider);

		final CtClass ctClass = classPool.get(SimpleColoredMethods.class.getName());
		final CtMethod ctMethod = ctClass.getDeclaredMethod("arraycopy");

		final @NonNull Optional<ColoredObject[][]> result = graphColorer.color(ctClass, ctMethod,
				new ColoredObject[] { null, null, null }, new ColoredObject[0], (inv, a, b) -> emptyMap(),
				(source, sink) -> {
					fail("We din't expect intersection to be found here");
				});

		assertNotNull(result);
		assertTrue(result.isPresent());

		// same as incoming
		assertEquals(ColorType.SourceData, result.get()[0][1].getColor().getType());
		// must be copied
		assertEquals(ColorType.SourceData, result.get()[0][2].getColor().getType());
	}

	@Test
	void testBadControllerExample() throws Exception {
		final GraphColorer graphColorer = new GraphColorer(emptyList(), classPool, colorlessGraphBuilder, userToCommand,
				dataProvider);

		final CtClass ctClass = classPool.get(BadControllerExample.class.getName());
		final CtMethod ctMethod = ctClass.getDeclaredMethod("sqlInjection");

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

	@Test
	void testConcatenation() throws Exception {
		final GraphColorer graphColorer = new GraphColorer(emptyList(), classPool, colorlessGraphBuilder, userToCommand,
				dataProvider);

		final CtClass ctClass = classPool.get(SimpleColoredMethods.class.getName());
		final CtMethod ctMethod = ctClass.getDeclaredMethod("concatenation");

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

}
