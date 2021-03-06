package io.github.vlsergey.secan4j.core.colorless;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Invocation {
	private final SourceCodePosition callSourceCodePosition;
	private final String className;
	private final String methodName;
	private final String methodSignature;
	private final DataNode[] parameters;
	private final DataNode[] results;
	private final boolean staticCall;
}