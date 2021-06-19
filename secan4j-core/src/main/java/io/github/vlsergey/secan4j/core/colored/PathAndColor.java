package io.github.vlsergey.secan4j.core.colored;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

import io.github.vlsergey.secan4j.core.utils.SetUtils;
import lombok.Data;
import lombok.NonNull;

@Data
public class PathAndColor {

	private static final PathAndColor EMPTY = new PathAndColor(emptyMap());

	private static final PaintedColor MIX_OF_COLORS = new PaintedColor(null, null, ColorType.Intersection);

	public static final String ROOT = "/";

	private static final PathAndColor forRootOnly(PaintedColor rootValue) {
		return new PathAndColor(singletonMap(singletonList(ROOT), rootValue));
	}

	public static @NonNull PathAndColor merge(final @NonNull PathAndColor picA, PathAndColor picB,
			BiConsumer<PaintedColor, PaintedColor> problemReporter) {
		if (picB.isEmpty()) {
			return picA;
		}
		if (picA.isEmpty()) {
			return picB;
		}

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

	private static PathAndColor mergeImpl(final @NonNull PathAndColor picA, final @NonNull PathAndColor picB,
			final @NonNull BiFunction<PaintedColor, PaintedColor, PaintedColor> colorMerged) {
		if (picA.path2colors.isEmpty())
			return picB;
		if (picB.path2colors.isEmpty())
			return picA;

		final Set<List<String>> commonPathes = SetUtils.intersection(picA.path2colors.keySet(),
				picB.path2colors.keySet());

		final Map<List<String>, PaintedColor> newPath2colors = new LinkedHashMap<>(
				picA.path2colors.size() + picB.path2colors.size() - commonPathes.size());

		picA.path2colors.entrySet().stream().filter(entry -> !commonPathes.contains(entry.getKey()))
				.forEach(entry -> newPath2colors.put(entry.getKey(), entry.getValue()));
		picB.path2colors.entrySet().stream().filter(entry -> !commonPathes.contains(entry.getKey()))
				.forEach(entry -> newPath2colors.put(entry.getKey(), entry.getValue()));

		for (List<String> commonPath : commonPathes) {
			PaintedColor colorA = picA.path2colors.get(commonPath);
			PaintedColor colorB = picB.path2colors.get(commonPath);
			newPath2colors.put(commonPath, colorMerged.apply(colorA, colorB));
		}

		return new PathAndColor(newPath2colors);
	}

	public static @NonNull PathAndColor sinkOnRoot(final TraceItem src, final @NonNull Confidence confidence) {
		return PathAndColor.forRootOnly(new PaintedColor(confidence, src, ColorType.SinkData));
	}

	public static @NonNull PathAndColor sourceOnRoot(final TraceItem src, final @NonNull Confidence confidence) {
		return forRootOnly(new PaintedColor(confidence, src, ColorType.SourceData));
	}

	private final Map<List<String>, PaintedColor> path2colors;

	public boolean isEmpty() {
		return path2colors.isEmpty();
	}

}
