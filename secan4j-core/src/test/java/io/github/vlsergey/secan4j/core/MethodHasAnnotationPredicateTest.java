package io.github.vlsergey.secan4j.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import io.github.vlsergey.secan4j.core.springwebmvc.BadControllerExample;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;

class MethodHasAnnotationPredicateTest {

	private final ClassPool classPool = ClassPool.getDefault();

	@Test
	void testTest() throws Exception {
		final CtClass ctClass = classPool.get(BadControllerExample.class.getName());
		final CtMethod sqlInjection = ctClass.getDeclaredMethod("sqlInjection");

		assertTrue(new MethodHasAnnotationPredicate(new AnnotatedByGraph(classPool), GetMapping.class.getName())
				.test(sqlInjection));
		assertTrue(new MethodHasAnnotationPredicate(new AnnotatedByGraph(classPool), RequestMapping.class.getName())
				.test(sqlInjection));
		assertFalse(new MethodHasAnnotationPredicate(new AnnotatedByGraph(classPool), DeleteMapping.class.getName())
				.test(sqlInjection));
	}

}
