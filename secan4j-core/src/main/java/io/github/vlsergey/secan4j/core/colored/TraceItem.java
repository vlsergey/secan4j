package io.github.vlsergey.secan4j.core.colored;

import java.util.Map;

public interface TraceItem {

	TraceItem findPrevious();

	Map<String, ?> describe();

}
