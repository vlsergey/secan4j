package io.github.vlsergey.secan4j.core.colorless;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import javassist.CtClass;
import javassist.CtField;
import javassist.bytecode.InstructionPrinter;
import javassist.bytecode.Opcode;
import javassist.bytecode.analysis.Type;
import lombok.AllArgsConstructor;
import lombok.NonNull;

@AllArgsConstructor
public class DataNodeFactory {

	private static final DataNode[] CONST_INTS = new DataNode[] {
			new DataNode("ICONST_0").setOperation(Opcode.ICONST_0).setType(Type.INTEGER),
			new DataNode("ICONST_1").setOperation(Opcode.ICONST_1).setType(Type.INTEGER),
			new DataNode("ICONST_2").setOperation(Opcode.ICONST_2).setType(Type.INTEGER),
			new DataNode("ICONST_3").setOperation(Opcode.ICONST_3).setType(Type.INTEGER),
			new DataNode("ICONST_4").setOperation(Opcode.ICONST_4).setType(Type.INTEGER),
			new DataNode("ICONST_5").setOperation(Opcode.ICONST_5).setType(Type.INTEGER) };

	private static final DataNode[] CONST_LONGS = new DataNode[] {
			new DataNode("LCONST_0").setOperation(Opcode.LCONST_0).setType(Type.LONG),
			new DataNode("LCONST_1").setOperation(Opcode.LCONST_1).setType(Type.LONG) };

	private static final DataNode CONST_NULL = new DataNode("ACONST_NULL").setOperation(Opcode.ACONST_NULL)
			.setType(Type.UNINIT);

	private final @NonNull ColorlessBlockGraphBuilder blockGraphBuilder;

	private final @NonNull NodeCollectors nodeCollectors;

	private final Map<SourceCodePosition, SourceCodePosition> uniquePositions = new HashMap<>();

	public DataNode newDataNode() {
		final DataNode result = new DataNode();
		populate(result);
		nodeCollectors.getAllNodes().accept(result);
		return result;
	}

	public GetFieldNode newGetField(final CtClass fieldClass, final CtField ctField) {
		final GetFieldNode result = new GetFieldNode(fieldClass, ctField);
		populate(result);
		nodeCollectors.getAllNodes().accept(result);
		return result;
	}

	public GetStaticNode newGetStatic(final CtClass fieldClass, final CtField ctField) {
		final GetStaticNode result = new GetStaticNode(fieldClass, ctField);
		populate(result);
		nodeCollectors.getAllNodes().accept(result);
		return result;
	}

	public DataNode newIntConst(int constantValue) {
		final DataNode result = CONST_INTS[constantValue];
		nodeCollectors.getAllNodes().accept(result);
		return result;
	}

	public DataNode newLongConst(int constantValue) {
		final DataNode result = CONST_LONGS[constantValue];
		nodeCollectors.getAllNodes().accept(result);
		return result;
	}

	public DataNode newNullConst() {
		nodeCollectors.getAllNodes().accept(CONST_NULL);
		return CONST_NULL;
	}

	private void populate(DataNode dataNode) {
		dataNode.operation = blockGraphBuilder.getCurrentOp();
		if (dataNode.description == null) {
			dataNode.description = InstructionPrinter.instructionString(blockGraphBuilder.getMethodCodeIterator(),
					blockGraphBuilder.getCurrentIndex(), blockGraphBuilder.getMethodConstPool());
		}

		SourceCodePosition sourceCodePosition = getCurrentSourceCodePosition();
		dataNode.setSourceCodePosition(sourceCodePosition);
	}

	SourceCodePosition getCurrentSourceCodePosition() {
		SourceCodePosition sourceCodePosition = new SourceCodePosition(blockGraphBuilder.getCtClass().getName(),
				blockGraphBuilder.getMethodInfo().getName(), blockGraphBuilder.getCurrentLineNumber());
		sourceCodePosition = uniquePositions.computeIfAbsent(sourceCodePosition, Function.identity());
		return sourceCodePosition;
	}
}
