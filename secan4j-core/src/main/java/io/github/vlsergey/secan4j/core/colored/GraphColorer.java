package io.github.vlsergey.secan4j.core.colored;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import io.github.vlsergey.secan4j.core.colorless.BlockDataGraph;
import io.github.vlsergey.secan4j.core.colorless.ColorlessGraphBuilder;
import io.github.vlsergey.secan4j.core.colorless.DataNode;
import io.github.vlsergey.secan4j.core.colorless.Invokation;
import io.github.vlsergey.secan4j.core.colorless.MethodParameterNode;
import javassist.ClassPool;
import javassist.CtBehavior;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.NotFoundException;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor
@Slf4j
public class GraphColorer {

	private final @NonNull ClassPool classPool;

	private final @NonNull ColorlessGraphBuilder colorlessGraphBuilder;

	private final @NonNull ColorProvider colorProvider;

	@SneakyThrows
	public void color(final @NonNull CtClass ctClass, final @NonNull CtMethod ctMethod,
			final @NonNull BiConsumer<TraceItem, TraceItem> onSourceSinkIntersection) {

		final BlockDataGraph colorlessGraph = colorlessGraphBuilder.buildGraph(ctClass, ctMethod);

		final Map<DataNode, PathAndColor> colors = new HashMap<>();
		for (final @NonNull DataNode dataNode : colorlessGraph.getAllNodes()) {
			if (dataNode instanceof MethodParameterNode) {
				final @NonNull MethodParameterNode typed = ((MethodParameterNode) dataNode);
				final @NonNull Optional<PathAndColor> opColor = colorProvider.getImplicitColor(typed.getCtClass(),
						typed.getCtMethod(), typed.getParameterIndex());
				opColor.ifPresent(color -> {
					if (log.isDebugEnabled()) {
						log.debug("Implicit color " + color + " assigned to " + typed);
					}
					colors.put(typed, color);
				});
			}
		}

		final @NonNull Cache<Invokation, CtClass> invClassCache = CacheBuilder.newBuilder().maximumSize(1 << 10)
				.build();
		final @NonNull Cache<Invokation, CtBehavior> invMethodsCache = CacheBuilder.newBuilder().maximumSize(1 << 10)
				.build();
		final @NonNull Cache<Invokation, BlockDataGraph> invGraphCache = CacheBuilder.newBuilder().maximumSize(1 << 10)
				.build();

		for (final @NonNull Invokation invokation : colorlessGraph.getInvokations()) {
			final String className = invokation.getClassName();
			if (className == null)
				continue;

			CtClass invTargetClass;
			try {
				invTargetClass = classPool.get(className);
			} catch (NotFoundException exc) {
				log.warn("Invokation class not found: " + className);
				continue;
			}
			invClassCache.put(invokation, invTargetClass);

			// TODO: down to child classes if only one present

			final CtBehavior invTargetMethod;
			try {
				invTargetMethod = invokation.getMethodName().equals("<init>")
						? invTargetClass.getConstructor(invokation.getMethodSignature())
						: invTargetClass.getMethod(invokation.getMethodName(), invokation.getMethodSignature());

				for (int i = 0; i < invTargetMethod.getParameterTypes().length; i++) {
					final DataNode dataNode = invokation.getParameters()[i];
					final @NonNull Optional<PathAndColor> opColor = colorProvider.getImplicitColor(invTargetClass,
							invTargetMethod, i);
					opColor.ifPresent(color -> {
						if (log.isDebugEnabled()) {
							log.debug("Implicit color " + color + " assigned to " + dataNode);
						}
						colors.put(dataNode, color);
					});
				}

			} catch (NotFoundException exc) {
				log.warn("Invokation method not found: " + className + "." + invokation.getMethodName() + ": '"
						+ invokation.getMethodSignature() + "'", exc);
				continue;
			}
			invMethodsCache.put(invokation, invTargetMethod);
		}

		// initial colors are assigned, now time to color nodes...
		boolean hasChanges = true;
		while (hasChanges) {
			// TODO: optimize by checking only changed nodes
			final Map<DataNode, PathAndColor> newColors = new HashMap<>();

			for (Invokation invokation : colorlessGraph.getInvokations()) {
				final CtClass invClass = invClassCache.getIfPresent(invokation);
				final CtBehavior invMethod = invMethodsCache.getIfPresent(invokation);
				if (invClass == null || invMethod == null || invMethod.isEmpty()) {
					continue;
				}

				final BlockDataGraph invGraph = invGraphCache.get(invokation,
						() -> colorlessGraphBuilder.buildGraph(invClass, invMethod));

				// XXX: do something!
			}

			hasChanges = !newColors.isEmpty();
			colors.putAll(newColors);
		}
	}

}
