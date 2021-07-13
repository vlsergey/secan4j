package io.github.vlsergey.secan4j.core;

import java.util.Arrays;
import java.util.function.Predicate;

import javax.annotation.concurrent.NotThreadSafe;

import javassist.CtMethod;
import javassist.bytecode.AnnotationsAttribute;
import javassist.bytecode.annotation.Annotation;
import lombok.AllArgsConstructor;
import lombok.NonNull;

@AllArgsConstructor
@NotThreadSafe
public class MethodHasAnnotationPredicate implements Predicate<CtMethod> {

	private final @NonNull AnnotatedByGraph annotatedByGraph;

	private final @NonNull String rootAnnotationClassName;

	private boolean isRequestMappingAnnotation(Annotation ann) {
		annotatedByGraph.populateIfNotPopulatedWith(ann.getTypeName());
		return annotatedByGraph.reachableNodes(rootAnnotationClassName).contains(ann.getTypeName());
	}

	@Override
	public boolean test(CtMethod t) {
		AnnotationsAttribute annAttr = (AnnotationsAttribute) t.getMethodInfo()
				.getAttribute(AnnotationsAttribute.visibleTag);
		if (annAttr == null) {
			return false;
		}
		return Arrays.stream(annAttr.getAnnotations()).anyMatch(this::isRequestMappingAnnotation);
	}

}
