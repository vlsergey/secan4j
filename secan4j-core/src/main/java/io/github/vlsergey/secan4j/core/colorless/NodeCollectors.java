package io.github.vlsergey.secan4j.core.colorless;

import java.util.function.Consumer;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class NodeCollectors {

	private final Consumer<DataNode> allNodes;

}
