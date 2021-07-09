package io.github.vlsergey.secan4j.core.colored;

import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.Arrays;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.github.vlsergey.secan4j.core.colored.brushes.CopierBrush;
import io.github.vlsergey.secan4j.core.colored.brushes.MethodParameterImplicitColorer;
import io.github.vlsergey.secan4j.core.user2command.UserToCommandInjectionColorer;
import io.github.vlsergey.secan4j.data.DataProvider;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import lombok.NonNull;

public class GraphColorerTest {

	private final ClassPool classPool = ClassPool.getDefault();
	private final DataProvider dataProvider = new DataProvider();
	private final UserToCommandInjectionColorer userToCommand = new UserToCommandInjectionColorer(dataProvider);

	@Test
	void testArraycopy() throws Exception {
		final GraphColorer graphColorer = new GraphColorer(
				Arrays.asList(new MethodParameterImplicitColorer(userToCommand)),
				singletonList(new CopierBrush(dataProvider)));

		final CtClass ctClass = classPool.get(SimpleColoredMethods.class.getName());
		final CtMethod ctMethod = ctClass.getDeclaredMethod("arraycopy");

		final @NonNull Optional<ColoredObject[][]> result = graphColorer.color(ctClass, ctMethod,
				new ColoredObject[] { null, null, null }, new ColoredObject[0], (source, sink) -> {
					fail("We din't expect intersection to be found here");
				});

		assertNotNull(result);
		assertTrue(result.isPresent());

		// same as incoming
		assertEquals(ColorType.SourceData, result.get()[0][1].getColor().getType());
		// must be copied
		assertEquals(ColorType.SourceData, result.get()[0][2].getColor().getType());
	}

}
