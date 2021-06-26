package io.github.vlsergey.secan4j.core.session;

import io.github.vlsergey.secan4j.core.colored.ColoredObject;
import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
class PaintingTaskResult {
	private final ColoredObject[] paintedIns;
	private final ColoredObject[] paintedOuts;
	private final long versionOfHeap;
}