package io.github.vlsergey.secan4j.core.colorless;

import javax.annotation.Nullable;

import javassist.bytecode.Opcode;
import javassist.bytecode.analysis.Type;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
// not a @Data -- different nodes with same content are different
public class DataNode {

	static final DataNode[] EMPTY_DATA_NODES = new DataNode[0];

	String description;

	DataNode[] inputs = EMPTY_DATA_NODES;

	int operation = Opcode.NOP;

	@Nullable
	SourceCodePosition sourceCodePosition;

	@NonNull
	Type type;

	DataNode() {
	}

	DataNode(String description) {
		this.description = description;
	}

	DataNode setType(final @NonNull Type type) {
		if (type == Type.TOP) {
			throw new IllegalArgumentException();
		}
		this.type = type;
		return this;
	}
}