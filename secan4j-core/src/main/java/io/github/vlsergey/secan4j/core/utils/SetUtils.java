package io.github.vlsergey.secan4j.core.utils;

import static java.util.Collections.emptySet;

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

		if (result.isEmpty()) {
			return emptySet();
		}
		return result;
	}

	public static <T> Set<T> join(Set<T> a, Set<T> b) {
		if (a.size() < b.size()) {
			return join(b, a);
		}
		assert a.size() >= b.size();

		if (a.isEmpty()) {
			return emptySet();
		}
		if (b.isEmpty()) {
			return a;
		}

		final Set<T> result = new HashSet<>(a);
		result.addAll(b);
		return result;
	}

}
