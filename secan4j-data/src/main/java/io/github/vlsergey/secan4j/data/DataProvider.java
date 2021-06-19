package io.github.vlsergey.secan4j.data;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NonNull;
import lombok.SneakyThrows;

public class DataProvider {

	@AllArgsConstructor
	@Data
	static class ResourcePathCandidate {
		final String path;
		final List<String> tokens;
	}

	private static final int CACHES_SIZE = 1 << 15;

	private static final SecanData EMPTY = new SecanData(emptyMap());

	private static final String RESOURCE_PREFIX = "/META-INF/secan4j/";

	private static final String RESOURCE_SUFFIX = ".yaml";

	private final Cache<String, SecanData> clsToData = CacheBuilder.newBuilder().concurrencyLevel(1)
			.maximumSize(CACHES_SIZE).build();

	private final Cache<String, Map<String, ?>> resourcePathToData = CacheBuilder.newBuilder().concurrencyLevel(1)
			.maximumSize(CACHES_SIZE).build();

	private final ObjectMapper yamlObjectMapper = new ObjectMapper(new YAMLFactory());

	@SneakyThrows
	public @NonNull SecanData getDataForClass(@NonNull String fqcn) {
		return clsToData.get(fqcn, () -> getDataForClassImpl(fqcn));
	}

	protected @NonNull SecanData getDataForClassImpl(@NonNull String fqcn) {
		final List<ResourcePathCandidate> candidates = getResourcePathCandidates(fqcn);
		for (ResourcePathCandidate candidate : candidates) {
			@NonNull
			Map<String, ?> data = load(candidate.getPath());
			if (!data.isEmpty()) {
				final List<String> tokens = candidate.getTokens();
				for (int i = tokens.size() - 1; i >= 0; i--) {
					String token = tokens.get(i);
					data = singletonMap(token, data);
				}

				return new SecanData(data);
			}
		}
		return EMPTY;
	}

	protected List<ResourcePathCandidate> getResourcePathCandidates(String fqcn) {
		final StringBuilder builder = new StringBuilder();
		final List<String> tokens = new ArrayList<>();

		final List<ResourcePathCandidate> result = new ArrayList<>();

		int afterPrevDot = 0;
		for (int charIndex = 0; charIndex < fqcn.length(); charIndex++) {
			final char charAt = fqcn.charAt(charIndex);
			if (charAt == '.') {
				tokens.add(fqcn.substring(afterPrevDot, charIndex));
				afterPrevDot = charIndex + 1;

				builder.setLength(0);
				builder.append(RESOURCE_PREFIX);
				builder.append(fqcn, 0, charIndex);
				builder.append(RESOURCE_SUFFIX);
				result.add(new ResourcePathCandidate(builder.toString(), new ArrayList<>(tokens)));
			} else if (charAt < 'a' || 'z' < charAt) {
				break;
			}
		}

		Collections.reverse(result);
		return result;
	}

	@SneakyThrows
	protected @NonNull Map<String, ?> load(final @NonNull String resourcePath) {
		return resourcePathToData.get(resourcePath, () -> loadImpl(resourcePath));
	}

	@SneakyThrows
	@SuppressWarnings("unchecked")
	protected @NonNull Map<String, ?> loadImpl(final @NonNull String resourcePath) {
		final InputStream is = DataProvider.class.getResourceAsStream(resourcePath);
		if (is == null) {
			return emptyMap();
		}
		try {
			return yamlObjectMapper.readValue(is, Map.class);
		} finally {
			is.close();
		}
	}

}
