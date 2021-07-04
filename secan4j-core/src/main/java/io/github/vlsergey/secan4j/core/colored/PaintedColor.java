package io.github.vlsergey.secan4j.core.colored;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NonNull;

@AllArgsConstructor
@Data
public final class PaintedColor {
	private final @NonNull Confidence confidence;
	private final TraceItem src;
	private final @NonNull ColorType type;
}