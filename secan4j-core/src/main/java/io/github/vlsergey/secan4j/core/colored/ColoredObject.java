package io.github.vlsergey.secan4j.core.colored;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singleton;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;

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

	private static final PaintedColor MIX_OF_COLORS = new PaintedColor(null, null, ColorType.Intersection);

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
		return new ColoredObject(color, emptyMap(), null, singleton(cls.getName().intern()));
	}

	public static @NonNull ColoredObject merge(final @Nullable ColoredObject picA, final @Nullable ColoredObject picB,
			BiConsumer<PaintedColor, PaintedColor> problemReporter) {

		return mergeImpl(picA, picB, (a, b) -> {
			if (a.type != b.type) {
				if (a.type != ColorType.Intersection && b.type != ColorType.Intersection) {
					problemReporter.accept(a, b);
				}
				return MIX_OF_COLORS;
			}
			return a.confidence.getValue() >= b.confidence.getValue() ? a : b;
		});
	}

	private static @Nullable ColoredObject mergeImpl(final @Nullable ColoredObject picA,
			final @Nullable ColoredObject picB,
			final @NonNull BiFunction<PaintedColor, PaintedColor, PaintedColor> colorMerged) {
		if (picA == null && picB == null)
			return null;
		if (picA != null && picB == null)
			return picA;
		if (picA == null && picB != null)
			return picB;

		final @NonNull PaintedColor mergedColor = colorMerged.apply(picA.color, picB.color);

		final @Nullable ColoredObject mergedItemOfArrayNode = mergeImpl(picA.itemOfArrayNode, picB.itemOfArrayNode,
				colorMerged);

		final @NonNull Set<String> mergedSeendClassesHere = SetUtils.join(picA.seenClassesHere, picB.seenClassesHere);

		final @NonNull Map<String, Map<String, ColoredObject>> mergedFieldNodes = mergeMaps(picA.fieldNodes,
				picB.fieldNodes, (fieldNodesA, fieldNodesB) -> mergeMaps(fieldNodesA, fieldNodesB,
						(fieldColorA, fieldColorB) -> mergeImpl(fieldColorA, fieldColorB, colorMerged)));

		return new ColoredObject(mergedColor, mergedFieldNodes, mergedItemOfArrayNode, mergedSeendClassesHere);
	}

	private static <K, V> @NonNull Map<K, V> mergeMaps(@NonNull Map<K, V> a, @NonNull Map<K, V> b,
			final @NonNull BiFunction<@NonNull V, @NonNull V, @NonNull V> valueMerger) {
		if (a.isEmpty())
			return b;
		if (b.isEmpty())
			return a;

		final Set<K> allKeys = SetUtils.join(a.keySet(), b.keySet());
		final Map<K, V> result = new HashMap<>(allKeys.size());
		for (K key : allKeys) {
			final V valueA = a.get(key);
			final V valueB = b.get(key);
			assert valueA != null || valueB != null;

			if (valueA == null) {
				result.put(key, valueB);
				continue;
			}
			if (valueB == null) {
				result.put(key, valueA);
				continue;
			}
			assert valueA != null && valueB != null;

			final @NonNull V mergedValue = valueMerger.apply(valueA, valueB);
			result.put(key, mergedValue);
		}

		return result;
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

	private final @NonNull Map<String, Map<String, ColoredObject>> fieldNodes;

	private final @Nullable ColoredObject itemOfArrayNode;

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

}
