package io.github.vlsergey.secan4j.core.colored;

import java.util.Map;

import javax.annotation.Nullable;

import io.github.vlsergey.secan4j.core.colorless.SourceCodePosition;
import lombok.NonNull;

public interface TraceItem {

	@NonNull
	Map<String, ?> describe();

	@Nullable
	TraceItem findPrevious();

	@Nullable
	default SourceCodePosition getSourceCodePosition() {
		return null;
	}

}
