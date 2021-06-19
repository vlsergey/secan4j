package io.github.vlsergey.secan4j.core.colorless;

import javassist.CtBehavior;
import javassist.CtClass;
import javassist.bytecode.analysis.Type;
import lombok.Getter;
import lombok.NonNull;
import lombok.SneakyThrows;

public class MethodParameterNode extends DataNode {

	@Getter
	private final @NonNull CtClass ctClass;

	@Getter
	private final @NonNull CtBehavior ctMethod;

	@Getter
	private final int parameterIndex;

	@SneakyThrows
	public MethodParameterNode(final @NonNull CtClass ctClass, final @NonNull CtBehavior ctMethod,
			final int parameterIndex) {
		super("arg#" + parameterIndex + " of " + ctMethod.getName());
		setType(Type.get(ctMethod.getParameterTypes()[parameterIndex]));
		this.ctClass = ctClass;
		this.ctMethod = ctMethod;
		this.parameterIndex = parameterIndex;
	}

}
