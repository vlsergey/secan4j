package io.github.vlsergey.secan4j.core.colored;

import lombok.Getter;

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

	public static Confidence max(Confidence a, Confidence b) {
		if (a == null) {
			return b;
		} else if (b == null) {
			return a;
		}

		return a.value >= b.value ? a : b;
	}

	public static Confidence min(Confidence a, Confidence b) {
		return a.value <= b.value ? a : b;
	}
}
