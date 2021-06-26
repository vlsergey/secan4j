package io.github.vlsergey.secan4j.core.colored;

import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

import io.github.vlsergey.secan4j.core.utils.SetUtils;
import javassist.CtClass;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NonNull;

@Data
public class PathToClassesAndColor {

	@AllArgsConstructor
	@Data
	public static final class ClassesAndColor {
		final Set<CtClass> classes;
		final PaintedColor color;
	}

	private static final PaintedColor MIX_OF_COLORS = new PaintedColor(null, null, ColorType.Intersection);

	public static final String ROOT = "/";

	private static final PathToClassesAndColor forRootOnly(final @NonNull CtClass ctClass, PaintedColor rootValue) {
		return new PathToClassesAndColor(
				singletonMap(singletonList(ROOT), new ClassesAndColor(singleton(ctClass), rootValue)));
	}

	public static @NonNull PathToClassesAndColor merge(final @NonNull PathToClassesAndColor picA,
			PathToClassesAndColor picB, BiConsumer<PaintedColor, PaintedColor> problemReporter) {
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

	private static @NonNull PathToClassesAndColor mergeImpl(final @NonNull PathToClassesAndColor picA,
			final @NonNull PathToClassesAndColor picB,
			final @NonNull BiFunction<PaintedColor, PaintedColor, PaintedColor> colorMerged) {
		if (picA.path2colors.isEmpty())
			return picB;
		if (picB.path2colors.isEmpty())
			return picA;

		final Set<List<String>> commonPathes = SetUtils.intersection(picA.path2colors.keySet(),
				picB.path2colors.keySet());

		final Map<List<String>, ClassesAndColor> newPath2colors = new LinkedHashMap<>(
				picA.path2colors.size() + picB.path2colors.size() - commonPathes.size());

		picA.path2colors.entrySet().stream().filter(entry -> !commonPathes.contains(entry.getKey()))
				.forEach(entry -> newPath2colors.put(entry.getKey(), entry.getValue()));
		picB.path2colors.entrySet().stream().filter(entry -> !commonPathes.contains(entry.getKey()))
				.forEach(entry -> newPath2colors.put(entry.getKey(), entry.getValue()));

		for (List<String> commonPath : commonPathes) {
			ClassesAndColor clsNclrA = picA.path2colors.get(commonPath);
			ClassesAndColor clsNclrB = picB.path2colors.get(commonPath);
			final @NonNull ClassesAndColor mixed = new ClassesAndColor(
					SetUtils.join(clsNclrA.getClasses(), clsNclrB.getClasses()),
					colorMerged.apply(clsNclrA.getColor(), clsNclrB.getColor()));
			newPath2colors.put(commonPath, mixed);
		}

		return new PathToClassesAndColor(newPath2colors);
	}

	public static @NonNull PathToClassesAndColor sinkOnRoot(final TraceItem src, final @NonNull CtClass elementType,
			final @NonNull Confidence confidence) {
		return PathToClassesAndColor.forRootOnly(elementType, new PaintedColor(confidence, src, ColorType.SinkData));
	}

	public static @NonNull PathToClassesAndColor sourceOnRoot(final @NonNull TraceItem src,
			final @NonNull CtClass elementType, final @NonNull Confidence confidence) {
		return forRootOnly(elementType, new PaintedColor(confidence, src, ColorType.SourceData));
	}

	private final Map<List<String>, ClassesAndColor> path2colors;

	public boolean isEmpty() {
		return path2colors.isEmpty();
	}

}
