package io.github.vlsergey.secan4j.core.colored;

import java.util.Optional;

import javassist.CtBehavior;
import javassist.CtClass;
import lombok.NonNull;

public interface ColorProvider {

	@NonNull
	Optional<ColoredObject> getImplicitColor(CtClass ctClass, CtBehavior ctMethod, int parameterIndex);

}