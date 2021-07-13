package io.github.vlsergey.secan4j.core.session;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import io.github.vlsergey.secan4j.core.springwebmvc.BadControllerExample;

class BadControllerExampleTest extends BasePaintingSessionTest {

	@Test
	void testSqlInjection() throws Exception {
		analyze(BadControllerExample.class, "sqlInjection");
		assertEquals(1, getIntesectionsCollector().getTraces().size());
	}

}
