package io.github.vlsergey.secan4j.core.session;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

import javax.annotation.Nonnull;

import io.github.vlsergey.secan4j.core.colored.TraceItem;
import lombok.Data;
import lombok.Getter;
import lombok.NonNull;

public class IntesectionsCollector implements BiConsumer<TraceItem, TraceItem> {

	@Data
	private static class SourceAndSink {

		private final @NonNull @Nonnull TraceItem sink;

		private final @NonNull @Nonnull TraceItem source;

		public SourceAndSink(@NonNull TraceItem source, @NonNull TraceItem sink) {
			this.source = source;
			this.sink = sink;
		}
	}

	@Getter
	private final @NonNull Map<SourceAndSink, List<TraceItem>> traces = new ConcurrentHashMap<>();

	@Override
	public void accept(TraceItem t, TraceItem u) {
		LinkedList<TraceItem> asList = toList(t, u);
		this.traces.put(new SourceAndSink(asList.getFirst(), asList.getLast()), asList);
	}

	public void clear() {
		this.traces.clear();
	}

	private LinkedList<TraceItem> toList(TraceItem t, TraceItem u) {
		LinkedList<TraceItem> result = new LinkedList<>();
		while (t != null) {
			result.addFirst(t);
			t = t.findPrevious();
		}
		while (u != null) {
			result.addLast(u);
			u = u.findPrevious();
		}
		return result;
	}

}
