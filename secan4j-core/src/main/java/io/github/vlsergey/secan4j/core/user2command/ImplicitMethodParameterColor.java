package io.github.vlsergey.secan4j.core.user2command;

import java.util.LinkedHashMap;
import java.util.Map;

import io.github.vlsergey.secan4j.core.colored.TraceItem;
import javassist.CtBehavior;
import javassist.CtClass;
import lombok.Data;

@Data
final class ImplicitMethodParameterColor implements TraceItem {

	private final String className;
	private final String methodName;
	private final String methodSignature;
	private final int parameterIndex;
	private final String prefixOfMessage;

	ImplicitMethodParameterColor(CtClass ctClass, CtBehavior ctMethod, int parameterIndex, String prefixOfMessage) {
		this.className = ctClass.getName().intern();
		this.methodName = ctMethod.getName().intern();
		this.methodSignature = ctMethod.getSignature().intern();
		this.parameterIndex = parameterIndex;
		this.prefixOfMessage = prefixOfMessage.intern();
	}

	@Override
	public Map<String, ?> describe() {
		final LinkedHashMap<String, Object> description = new LinkedHashMap<>();
		description.put("type", "MethodParameterAnnotations");

		description.put("className", className);
		description.put("methodName", methodName);
		description.put("methodSignature", methodSignature);
		description.put("parameterIndex", parameterIndex);

		description.put("message", prefixOfMessage + " argument #" + parameterIndex + " of method '" + methodName
				+ "' of class " + className);
		return description;
	}

	@Override
	public TraceItem findPrevious() {
		return null;
	}

	@Override
	public String toString() {
		return "MethodParameterTraceItem [" + (String) describe().get("message") + "]";
	}

}