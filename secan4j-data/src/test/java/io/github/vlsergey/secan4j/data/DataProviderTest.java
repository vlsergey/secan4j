package io.github.vlsergey.secan4j.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.sql.ResultSet;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.vlsergey.secan4j.data.DataProvider.ResourcePathCandidate;

class DataProviderTest {

	@SuppressWarnings("unchecked")
	@Test
	void testLoad() {
		final Map<String, ?> javaSql = new DataProvider().loadImpl("/META-INF/secan4j/java.sql.yaml");
		final Map<String, ?> conn = (Map<String, ?>) javaSql.get("Connection");
		assertNotNull(conn);

		final Map<String, ?> psMethod = (Map<String, ?>) conn.get("prepareStatement");
		assertNotNull(psMethod);

		final List<?> psMethodArgs = (List<?>) psMethod.get("arguments");
		assertNotNull(psMethodArgs);

		final List<?> firstArgAnnotations = (List<?>) psMethodArgs.get(0);
		assertEquals("Command", firstArgAnnotations.get(0));
	}

	@Test
	void testGetResourcePathCandidates() {
		List<ResourcePathCandidate> actual = new DataProvider().getResourcePathCandidates(ResultSet.class.getName());
		assertEquals(Arrays.asList( //
				new ResourcePathCandidate("/META-INF/secan4j/java.sql.yaml", Arrays.asList("java", "sql")), //
				new ResourcePathCandidate("/META-INF/secan4j/java.yaml", Arrays.asList("java")) //
		), actual);
	}

}
