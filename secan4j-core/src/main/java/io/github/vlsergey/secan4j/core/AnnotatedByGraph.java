package io.github.vlsergey.secan4j.core;

import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import javax.annotation.concurrent.ThreadSafe;

import com.google.common.graph.GraphBuilder;
import com.google.common.graph.Graphs;
import com.google.common.graph.MutableGraph;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.NotFoundException;
import javassist.bytecode.AnnotationsAttribute;
import javassist.bytecode.annotation.Annotation;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor
@ThreadSafe
@Slf4j
public class AnnotatedByGraph {

	private final @NonNull MutableGraph<String> annotatedByGraph = GraphBuilder.directed().allowsSelfLoops(true)
			.build();

	private final @NonNull ClassPool classPool;

	private final @NonNull Map<String, Set<String>> reachableNodesCache = new HashMap<>();

	@Synchronized
	public void populateIfNotPopulatedWith(String annotationClassName) {
		if (!annotatedByGraph.addNode(annotationClassName)) {
			return;
		}
		reachableNodesCache.clear();

		Deque<String> toCheck = new LinkedList<>();
		toCheck.add(annotationClassName);
		while (!toCheck.isEmpty()) {
			String toCheckClassName = toCheck.removeFirst();

			CtClass annClass;
			try {
				annClass = classPool.get(toCheckClassName);
			} catch (NotFoundException exc) {
				log.warn("Annotation with class name " + toCheckClassName + " not available in classpath");
				continue;
			}
			AnnotationsAttribute aInfo = (AnnotationsAttribute) annClass.getClassFile()
					.getAttribute(AnnotationsAttribute.visibleTag);
			if (aInfo != null) {
				for (Annotation ann2 : aInfo.getAnnotations()) {

					if (annotatedByGraph.addNode(ann2.getTypeName())) {
						toCheck.add(ann2.getTypeName());
					}

					annotatedByGraph.putEdge(ann2.getTypeName(), toCheckClassName);
				}
			}
		}
	}

	@Synchronized
	public Set<String> reachableNodes(String rootAnnotationClassName) {
		populateIfNotPopulatedWith(rootAnnotationClassName);
		return reachableNodesCache.computeIfAbsent(rootAnnotationClassName,
				x -> Graphs.reachableNodes(annotatedByGraph, x));
	}

}
