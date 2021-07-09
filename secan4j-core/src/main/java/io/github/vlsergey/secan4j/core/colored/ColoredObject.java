package io.github.vlsergey.secan4j.core.colored;

import static java.util.Collections.singleton;

import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.annotation.Nullable;

import io.github.vlsergey.secan4j.core.utils.SetUtils;
import javassist.CtClass;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NonNull;
import lombok.With;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Data
public class ColoredObject {

	private static void demultiplex(final @NonNull ColoredObject[] src, final @NonNull ColoredObject[] buffer,
			int pointer, final @NonNull Consumer<@NonNull ColoredObject[]> consumer) {
		if (pointer == src.length) {
			final @NonNull ColoredObject[] bufferCopy = Arrays.copyOf(buffer, buffer.length);
			consumer.accept(bufferCopy);
			return;
		}

		final ColoredObject co = src[pointer];
		if (co == null) {
			demultiplex(src, buffer, pointer + 1, consumer);
			return;
		}

		co.demultiplex(d -> {
			buffer[pointer] = d;
			demultiplex(src, buffer, pointer + 1, consumer);
		});
	}

	public static void demultiplex(final @NonNull ColoredObject[] src,
			final @NonNull Consumer<@NonNull ColoredObject[]> consumer) {
		demultiplex(src, Arrays.copyOf(src, src.length), 0, consumer);
	}

	public static ColoredObject forRootOnly(final @NonNull CtClass cls, PaintedColor color) {
		return new ColoredObject(color, singleton(cls.getName().intern()));
	}

	public static @NonNull ColoredObject merge(final @Nullable ColoredObject picA, final @Nullable ColoredObject picB,
			final @NonNull BiConsumer<PaintedColor, PaintedColor> problemReporter) {

		return mergeImpl(picA, picB, (a, b) -> {
			if (a.getType() != b.getType()) {
				if (a.getType() == ColorType.Intersection)
					return a;
				if (b.getType() == ColorType.Intersection)
					return a;

				problemReporter.accept(a, b);
				return new PaintedColor(Confidence.min(a.getConfidence(), b.getConfidence()).max(Confidence.CALCULATED),
						null, ColorType.Intersection);
			}
			return a.getConfidence().getValue() >= b.getConfidence().getValue() ? a : b;
		});
	}

	private static @Nullable ColoredObject mergeImpl(final @Nullable ColoredObject picA,
			final @Nullable ColoredObject picB,
			final @NonNull BiFunction<PaintedColor, PaintedColor, PaintedColor> colorMerged) {
		if (Objects.equals(picA, picB))
			return picA;
		if (picA != null && (picB == null || picA.getColor().getType() == ColorType.Intersection))
			return picA;
		if (picB != null && (picA == null || picB.getColor().getType() == ColorType.Intersection))
			return picB;

		final @NonNull PaintedColor mergedColor = colorMerged.apply(picA.color, picB.color);
		final @NonNull Set<String> mergedSeendClassesHere = SetUtils.join(picA.seenClassesHere, picB.seenClassesHere);

		return new ColoredObject(mergedColor, mergedSeendClassesHere);
	}

	/**
	 * Merge colors in a way that the most dangerous is preserved
	 */
	public static @NonNull ColoredObject mergeToMostDangerous(final @Nullable ColoredObject picA,
			final @Nullable ColoredObject picB) {

		return mergeImpl(picA, picB, (a, b) -> {
			if (a.getType() != b.getType()) {
				if (a.getType() == ColorType.SourceData) {
					return a;
				} else if (b.getType() == ColorType.SourceData) {
					return b;
				}

				if (a.getType() == ColorType.Intersection) {
					return a;
				} else if (b.getType() == ColorType.Intersection) {
					return b;
				}

				throw new AssertionError("Both types are SinkData, but they are not equal (?)");
			}
			return a.getConfidence().getValue() >= b.getConfidence().getValue() ? a : b;
		});
	}

	public static @NonNull ColoredObject sinkOnRoot(final TraceItem src, final @NonNull CtClass elementType,
			final @NonNull Confidence confidence) {
		return ColoredObject.forRootOnly(elementType, new PaintedColor(confidence, src, ColorType.SinkData));
	}

	public static @NonNull ColoredObject sourceOnRoot(final TraceItem src, final @NonNull CtClass elementType,
			final @NonNull Confidence confidence) {
		return ColoredObject.forRootOnly(elementType, new PaintedColor(confidence, src, ColorType.SourceData));
	}

	private final @NonNull PaintedColor color;

	@With
	private final @NonNull Set<String> seenClassesHere;

	/**
	 * Calls back multiple times -- one per each seen class (with value "limited" to
	 * single seen class)
	 */
	public void demultiplex(final @NonNull Consumer<@NonNull ColoredObject> consumer) {
		// TODO: implement deeper demultiplexing
		// TODO: filter fieldNodes basing on seen class
		this.seenClassesHere.forEach(cls -> consumer.accept(this.withSeenClassesHere(singleton(cls))));
	}

	public ColoredObject withNewTraceItem(Function<TraceItem, TraceItem> traceItemUpdater) {
		return new ColoredObject(
				new PaintedColor(color.getConfidence(), traceItemUpdater.apply(color.getSrc()), color.getType()),
				seenClassesHere);
	}

}
