package io.github.vlsergey.secan4j.core.session;

import io.github.vlsergey.secan4j.core.colored.TraceItem;
import io.github.vlsergey.secan4j.core.colorless.SourceCodePosition;

public class TestTraceItem implements TraceItem {

	public static final TestTraceItem INSTANCE = new TestTraceItem();

	@Override
	public TraceItem findPrevious() {
		return null;
	}

	@Override
	public String getMessage() {
		return "test";
	}

	@Override
	public SourceCodePosition getSourceCodePosition() {
		return null;
	}
}