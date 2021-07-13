package io.github.vlsergey.secan4j.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;

import org.junit.jupiter.api.Test;

import io.github.vlsergey.secan4j.core.springwebmvc.BadControllerExample;

class Secan4jTest {

	@Test
	void testMain() throws IOException, InterruptedException {
		final ProcessBuilder builder = new ProcessBuilder("java", "-cp", System.getProperty("java.class.path"),
				Secan4j.class.getName(), "--basePackage=" + BadControllerExample.class.getPackageName(),
				System.getProperty("java.class.path"));
		builder.redirectError(Redirect.INHERIT);
		builder.redirectOutput(Redirect.INHERIT);

		final Process process = builder.start();
		int exitCode = process.waitFor();
		assertEquals(1, exitCode);
	}

}
