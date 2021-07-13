package io.github.vlsergey.secan4j.core.colorless;

import lombok.Data;
import lombok.NonNull;

@Data
public class SourceCodePosition {

	public SourceCodePosition(final @NonNull String className, final @NonNull String methodName, int sourceLine) {
		this.className = className.intern();
		this.methodName = methodName.intern();
		this.sourceLine = sourceLine;
	}

	private final @NonNull String className;
	private final @NonNull String methodName;
	private final int sourceLine;

}
