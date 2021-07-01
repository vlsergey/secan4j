package io.github.vlsergey.secan4j.core.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.function.BiConsumer;

import org.junit.jupiter.api.Test;

import io.github.vlsergey.secan4j.core.colored.ColorType;
import io.github.vlsergey.secan4j.core.colored.ColoredObject;
import io.github.vlsergey.secan4j.core.colored.SimpleColoredMethods;
import io.github.vlsergey.secan4j.core.colored.TraceItem;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import lombok.NonNull;

class PaintingSessionTest {

	private final ClassPool classPool = ClassPool.getDefault();

	private static final @NonNull BiConsumer<TraceItem, TraceItem> noIntersectionExpected = (source, sink) -> {
		fail("We din't expect intersection to be found so soon");
	};

	@Test
	void testAppend() throws Exception {
		final CtClass ctClass = classPool.get(SimpleColoredMethods.class.getName());
		final CtMethod ctMethod = ctClass.getDeclaredMethod("append");

		PaintingSession paintingSession = new PaintingSession(classPool, noIntersectionExpected);

		final ColoredObject[][] result = paintingSession.analyze(ctMethod);
		assertNotNull(result);
		assertEquals(ColorType.SourceData, result[0][2].getColor().getType());
	}

	@Test
	void testConcatenation() throws Exception {
		final CtClass ctClass = classPool.get(SimpleColoredMethods.class.getName());
		final CtMethod ctMethod = ctClass.getDeclaredMethod("concatenation");

		PaintingSession paintingSession = new PaintingSession(classPool, noIntersectionExpected);

		final ColoredObject[][] result = paintingSession.analyze(ctMethod);
		assertNotNull(result);
		assertEquals(ColorType.SourceData, result[1][0].getColor().getType());
	}

}
