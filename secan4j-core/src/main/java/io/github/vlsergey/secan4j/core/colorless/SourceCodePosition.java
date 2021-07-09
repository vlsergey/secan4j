package io.github.vlsergey.secan4j.core.colorless;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NonNull;

@AllArgsConstructor
@Data
public class SourceCodePosition {

	private final @NonNull String className;
	private final @NonNull String methodName;
	private final int sourceLine;
	private final int sourceOffset;
}
