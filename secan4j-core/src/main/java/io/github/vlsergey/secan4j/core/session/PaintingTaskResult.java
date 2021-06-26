package io.github.vlsergey.secan4j.core.session;

import io.github.vlsergey.secan4j.core.colored.PathToClassesAndColor;
import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
class PaintingTaskResult {
	private final PathToClassesAndColor[] paintedIns;
	private final PathToClassesAndColor[] paintedOuts;
	private final long versionOfHeap;
}