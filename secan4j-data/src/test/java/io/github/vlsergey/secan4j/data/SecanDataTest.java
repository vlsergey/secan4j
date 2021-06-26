package io.github.vlsergey.secan4j.data;

import static java.util.Collections.singleton;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestParam;

import io.github.vlsergey.secan4j.annotations.Command;
import io.github.vlsergey.secan4j.annotations.CopyColorsFrom;
import io.github.vlsergey.secan4j.annotations.CopyColorsTo;
import io.github.vlsergey.secan4j.annotations.UserProvided;
import javassist.ClassPool;
import javassist.CtMethod;
import javassist.bytecode.SignatureAttribute;
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

		final @NonNull SecanData data = new DataProvider().getDataForClassImpl(System.class.getName());
		final Set<Class<?>>[] args = data.getForMethodArguments(System.class.getName(), ctMethod.getName(),
				SignatureAttribute.toMethodSignature(ctMethod.getSignature()));

		assertEquals(singleton(CopyColorsFrom.class), args[0]);
		assertEquals(singleton(CopyColorsTo.class), args[2]);
	}

	@Test
	void testGetForMethodArguments_prepareStatement() throws Exception {
		final CtMethod ctMethod = new ClassPool(true).getMethod(Connection.class.getName(), "prepareStatement");

		final @NonNull SecanData data = new DataProvider().getDataForClassImpl(Connection.class.getName());
		final Set<Class<?>>[] args = data.getForMethodArguments(Connection.class.getName(), ctMethod.getName(),
				SignatureAttribute.toMethodSignature(ctMethod.getSignature()));

		assertEquals(singleton(Command.class), args[0]);
	}

}
