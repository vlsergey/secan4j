package io.github.vlsergey.secan4j.core.colored;

import static java.util.Collections.singletonMap;

import java.util.Map;

import javax.annotation.Nullable;

import io.github.vlsergey.secan4j.core.colorless.SourceCodePosition;
import lombok.NonNull;

public interface TraceItem {

	default @NonNull Map<String, ?> describe() {
		return singletonMap("message", getMessage());
	}

	@Nullable
	TraceItem findPrevious();

	@Nullable
	SourceCodePosition getSourceCodePosition();

	String getMessage();

}
