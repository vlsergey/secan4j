package io.github.vlsergey.secan4j.data;

import static java.util.Collections.singleton;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestParam;

import io.github.vlsergey.secan4j.annotations.Command;
import io.github.vlsergey.secan4j.annotations.CopyAttributesFrom;
import io.github.vlsergey.secan4j.annotations.CopyAttributesTo;
import io.github.vlsergey.secan4j.annotations.UserProvided;
import javassist.ClassPool;
import javassist.CtMethod;
import lombok.NonNull;

class SecanDataTest {

	@Test
	void testGetForAnnotation() {
		final @NonNull SecanData data = new DataProvider().getDataForClassImpl(RequestParam.class.getName());
		final Set<Class<?>> set = data.getForAnnotation(RequestParam.class.getName());
		assertEquals(singleton(UserProvided.class), set);
	}

	@Test
	void testGetForMethodArguments_arraycopy() throws Exception {
		final CtMethod ctMethod = new ClassPool(true).getMethod(System.class.getName(), "arraycopy");

		final Set<Class<?>>[] args = new DataProvider().getForMethodArguments(System.class.getName(),
				ctMethod.getName(), ctMethod.getSignature());

		assertEquals(singleton(CopyAttributesFrom.class), args[0]);
		assertEquals(singleton(CopyAttributesTo.class), args[2]);
	}

	@Test
	void testGetForMethodArguments_prepareStatement() throws Exception {
		final CtMethod ctMethod = new ClassPool(true).getMethod(Connection.class.getName(), "prepareStatement");

		final Set<Class<?>>[] args = new DataProvider().getForMethodArguments(Connection.class.getName(),
				ctMethod.getName(), ctMethod.getSignature());

		assertEquals(singleton(Command.class), args[0]);
	}

}
