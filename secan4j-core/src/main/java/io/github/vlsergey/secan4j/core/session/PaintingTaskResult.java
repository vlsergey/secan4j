package io.github.vlsergey.secan4j.core.session;

import io.github.vlsergey.secan4j.core.colored.PathAndColor;
import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
class PaintingTaskResult {
	private final PathAndColor[] paintedIns;
	private final PathAndColor[] paintedOuts;
	private final long versionOfHeap;
}