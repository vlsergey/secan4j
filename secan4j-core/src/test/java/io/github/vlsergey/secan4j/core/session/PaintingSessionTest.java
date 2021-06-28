package io.github.vlsergey.secan4j.core.session;

import static org.junit.jupiter.api.Assertions.fail;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import io.github.vlsergey.secan4j.core.colored.ColoredObject;
import io.github.vlsergey.secan4j.core.colored.SimpleColoredMethods;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;

class PaintingSessionTest {

	private final ClassPool classPool = ClassPool.getDefault();

	@Test
	void testAppend() throws Exception {
		final CtClass ctClass = classPool.get(SimpleColoredMethods.class.getName());
		final CtMethod ctMethod = ctClass.getDeclaredMethod("append");

		PaintingSession paintingSession = new PaintingSession(classPool, (source, sink) -> {
			fail("We din't expect intersection to be found so soon");
		});

		final ColoredObject[][] result = paintingSession.analyze(ctMethod);
		System.out.println(Arrays.toString(result));
	}

	@Test
	void testConcatenation() throws Exception {
		final CtClass ctClass = classPool.get(SimpleColoredMethods.class.getName());
		final CtMethod ctMethod = ctClass.getDeclaredMethod("concatenation");

		PaintingSession paintingSession = new PaintingSession(classPool, (source, sink) -> {
			fail("We din't expect intersection to be found so soon");
		});

		final ColoredObject[][] result = paintingSession.analyze(ctMethod);
		System.out.println(result);
	}

}
