package io.github.vlsergey.secan4j.core.colored;

import lombok.Getter;
import lombok.NonNull;

public enum Confidence {

	EXPLICITLY_AND_ENFORCE(12),

	EXPLICITLY(10),

	CONFIGURATION(8),

	CALCULATED(6),

	ASSUMED(4),

	;

	@Getter
	private int value;

	private Confidence(int value) {
		this.value = value;
	}

	public Confidence max(Confidence other) {
		return max(this, other);
	}

	public static Confidence max(Confidence a, Confidence b) {
		if (a == null) {
			return b;
		} else if (b == null) {
			return a;
		}

		return a.value >= b.value ? a : b;
	}

	public static @NonNull Confidence min(final @NonNull Confidence a, final @NonNull Confidence b) {
		return a.value <= b.value ? a : b;
	}
}
