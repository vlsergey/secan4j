package io.github.vlsergey.secan4j.core.colorless;

import java.util.Deque;

import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public class BlockDataGraph {
	private final DataNode[] allNodes;
	private final DataNode[] incLocalNodes;
	private final Deque<DataNode> incStackNodes;
	private final Invocation[] invokations;
	private final DataNode[] methodParamNodes;
	private final DataNode[] methodReturnNodes;
	private final DataNode[] outLocalNodes;
	private final DataNode[] outReturns;
	private final Deque<DataNode> outStackNodes;
	private final PutFieldNode[] putFieldNodes;
	private final PutStaticNode[] putStaticNodes;
}