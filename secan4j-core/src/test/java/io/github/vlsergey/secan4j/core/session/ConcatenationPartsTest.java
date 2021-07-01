package io.github.vlsergey.secan4j.core.session;

import static io.github.vlsergey.secan4j.core.colored.ColorType.SourceData;
import static java.util.Collections.singletonMap;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.Arrays;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.IntFunction;

import javax.annotation.Nullable;

import org.junit.jupiter.api.Test;

import io.github.vlsergey.secan4j.core.colored.ColorType;
import io.github.vlsergey.secan4j.core.colored.ColoredObject;
import io.github.vlsergey.secan4j.core.colored.Confidence;
import io.github.vlsergey.secan4j.core.colored.PaintedColor;
import io.github.vlsergey.secan4j.core.colored.TraceItem;
import javassist.ClassPool;
import javassist.CtBehavior;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.Modifier;
import javassist.NotFoundException;
import lombok.NonNull;

class ConcatenationPartsTest {

	private final static @NonNull BiConsumer<TraceItem, TraceItem> noIntersectionExpected = (source, sink) -> {
		fail("We din't expect intersection to be found here");
	};

	private static final TraceItem testTraceItem = new TraceItem() {

		@Override
		public Map<String, ?> describe() {
			return singletonMap("description", "test source");
		}

		@Override
		public TraceItem findPrevious() {
			return null;
		}
	};

	private static <S, R> R[] map(S[] src, Function<S, R> func, IntFunction<R[]> arraySupplier) {
		return Arrays.stream(src).map(x -> x == null ? null : func.apply(x)).toArray(arraySupplier);
	}

	private static ColoredObject[] toColoredObjects(final CtClass[] classes, ColorType[] colors)
			throws NotFoundException {
		ColoredObject[] inObjects = new ColoredObject[colors.length];
		for (int i = 0; i < colors.length; i++) {
			inObjects[i] = colors[i] == null ? null
					: ColoredObject.forRootOnly(classes[i],
							new PaintedColor(Confidence.EXPLICITLY, testTraceItem, colors[i]));
		}
		return inObjects;
	}

	private static ColorType[] toColorType(ColoredObject[] colors) {
		return map(colors, c -> c.getColor().getType(), ColorType[]::new);
	}

	private static ColorType[][] toColorType(ColoredObject[][] colors) {
		return map(colors, ConcatenationPartsTest::toColorType, ColorType[][]::new);
	}

	private final ClassPool classPool = ClassPool.getDefault();

	private @NonNull ColorType[][] analyze(final @NonNull Class<?> cls, final @NonNull String methodName,
			final @NonNull String signature, ColorType[] ins, ColorType[] outs) throws Exception {
		return analyze(cls, methodName, signature, ins, outs, null);
	}

	private @NonNull ColorType[][] analyze(final @NonNull Class<?> cls, final @NonNull String methodName,
			final @NonNull String signature, ColorType[] ins, ColorType[] outs, @Nullable Class<?>[] inTypes)
			throws Exception {
		final CtClass ctClass = classPool.get(cls.getName());
		final CtBehavior ctBehavior = "<init>".equals(methodName) ? ctClass.getConstructor(signature)
				: ctClass.getMethod(methodName, signature);

		assert outs.length < 2;
		assert (outs.length == 1) == (ctBehavior instanceof CtMethod
				&& ((CtMethod) ctBehavior).getReturnType() != CtClass.voidType);

		PaintingSession paintingSession = new PaintingSession(classPool, noIntersectionExpected);

		final CtClass[] actualInTypes;
		if (!(ctBehavior instanceof CtMethod) || !Modifier.isStatic(((CtMethod) ctBehavior).getModifiers())) {
			actualInTypes = new CtClass[ctBehavior.getParameterTypes().length + 1];
			actualInTypes[0] = ctClass;
			System.arraycopy(ctBehavior.getParameterTypes(), 0, actualInTypes, 1,
					ctBehavior.getParameterTypes().length);
		} else {
			actualInTypes = ctBehavior.getParameterTypes();
		}
		if (inTypes != null) {
			for (int i = 0; i < inTypes.length; i++) {
				if (inTypes[i] != null) {
					actualInTypes[i] = classPool.get(inTypes[i].getName());
				}
			}
		}

		final ColoredObject[][] analyzeResult = paintingSession.analyze(ctBehavior,
				toColoredObjects(actualInTypes, ins), outs.length == 0 ? new ColoredObject[0]
						: toColoredObjects(new CtClass[] { ((CtMethod) ctBehavior).getReturnType() }, outs));

		final ColoredObject[][] result = analyzeResult != null ? analyzeResult
				: new ColoredObject[][] { new ColoredObject[ins.length], new ColoredObject[outs.length] };
		return toColorType(result);
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
