package io.github.vlsergey.secan4j.core.utils;

import java.util.HashSet;
import java.util.Set;

public class SetUtils {

	public static <T> Set<T> intersection(Set<T> a, Set<T> b) {
		if (a.size() < b.size()) {
			return intersection(b, a);
		}

		final Set<T> result = new HashSet<>(b.size());
		for (T item : b) {
			if (a.contains(item)) {
				result.add(item);
			}
		}
		return result;
	}

}
