package io.github.vlsergey.secan4j.core.user2command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.github.vlsergey.secan4j.core.colored.ColorType;
import io.github.vlsergey.secan4j.core.colored.ColoredObject;
import io.github.vlsergey.secan4j.core.springwebmvc.BadControllerExample;
import io.github.vlsergey.secan4j.data.DataProvider;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import lombok.NonNull;

class UserToCommandInjectionColorerTest {

	private final ClassPool classPool = ClassPool.getDefault();
	private final DataProvider dataProvider = new DataProvider();
	private final UserToCommandInjectionColorer colorer = new UserToCommandInjectionColorer(dataProvider);

	@Test
	void testGetImplicitColor() throws Exception {
		final CtClass ctClass = classPool.get(BadControllerExample.class.getName());
		final CtMethod ctMethod = ctClass.getDeclaredMethod("sqlInjection");

		final @NonNull Optional<ColoredObject> implicitColor = colorer.getImplicitColor(ctClass, ctMethod, 0);
		assertTrue(implicitColor.isPresent());
		assertEquals(ColorType.SourceData, implicitColor.get().getColor().getType());
	}

}
