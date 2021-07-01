package io.github.vlsergey.secan4j.core.session;

import static io.github.vlsergey.secan4j.core.colored.ColorType.SourceData;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import io.github.vlsergey.secan4j.core.colored.ColorType;

class ConcatenationPartsTest extends BasePaintingSessionTest {

	@Test
	void testArraysCopyOfRange() throws Exception {
		assertArrayEquals(new ColorType[][] { { SourceData, null, null }, { SourceData } }, analyze(Arrays.class,
				"copyOfRange", "([CII)[C", new ColorType[] { SourceData, null, null }, new ColorType[] { null }));
	}

	@Test
	void testStringBuilderAppendObject() throws Exception {
		assertArrayEquals(new ColorType[][] { { SourceData, SourceData }, { SourceData } },
				analyze(StringBuilder.class, "append", "(Ljava/lang/Object;)Ljava/lang/StringBuilder;",
						new ColorType[] { null, SourceData }, new ColorType[] { null },
						new Class[] { null, String.class }));
	}

	@Test
	void testStringBuilderAppendString() throws Exception {
		assertArrayEquals(new ColorType[][] { { SourceData, SourceData }, { SourceData } },
				analyze(StringBuilder.class, "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;",
						new ColorType[] { null, SourceData }, new ColorType[] { null }));
	}

	@Test
	void testStringBuilderToString() throws Exception {
		assertArrayEquals(new ColorType[][] { { SourceData }, { SourceData } }, analyze(StringBuilder.class, "toString",
				"()Ljava/lang/String;", new ColorType[] { SourceData }, new ColorType[] { null }));
	}

	@Test
	void testStringInit() throws Exception {
		assertArrayEquals(new ColorType[][] { { SourceData, SourceData }, {} }, analyze(String.class, "<init>",
				"(Ljava/lang/String;)V", new ColorType[] { null, SourceData }, new ColorType[] {}));
	}

	@Test
	void testStringValueOf() throws Exception {
		// transfer color from argument to <this>
		assertArrayEquals(new ColorType[][] { { SourceData }, { SourceData } },
				analyze(String.class, "valueOf", "(Ljava/lang/Object;)Ljava/lang/String;",
						new ColorType[] { SourceData }, new ColorType[] { null }, new Class[] { String.class }));
	}

}
