package io.github.vlsergey.secan4j.core.colorless;

import javassist.CtClass;
import javassist.CtField;
import lombok.Data;
import lombok.Getter;
import lombok.NonNull;

@Data
public class PutFieldNode {

	@Getter
	private final @NonNull CtClass ctClass;

	@Getter
	private final @NonNull CtField ctField;

	@Getter
	private final @NonNull DataNode objectRef;

	@Getter
	private final @NonNull DataNode value;

}
