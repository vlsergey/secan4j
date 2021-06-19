package io.github.vlsergey.secan4j.core.colorless;

import javassist.CtClass;
import javassist.CtField;
import lombok.Getter;
import lombok.NonNull;

public class GetFieldNode extends DataNode {

	@Getter
	private final @NonNull CtClass ctClass;

	@Getter
	private final @NonNull CtField ctField;

	public GetFieldNode(final @NonNull CtClass ctClass, final @NonNull CtField ctField) {
		super("getfield " + ctClass.getName() + "." + ctField.getName());
		this.ctClass = ctClass;
		this.ctField = ctField;
	}

	public @NonNull DataNode getObjectRef() {
		return this.inputs[0];
	}

}
