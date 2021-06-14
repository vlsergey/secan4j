package io.github.vlsergey.secan4j.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.CtPrimitiveType;
import javassist.Modifier;
import javassist.NotFoundException;
import javassist.bytecode.CodeAttribute;
import javassist.bytecode.CodeIterator;
import javassist.bytecode.ConstPool;
import javassist.bytecode.Descriptor;
import javassist.bytecode.InstructionPrinter;
import javassist.bytecode.Mnemonic;
import javassist.bytecode.Opcode;
import javassist.bytecode.analysis.ControlFlow;
import javassist.bytecode.analysis.ControlFlow.Block;
import javassist.bytecode.analysis.ControlFlow.Node;
import javassist.bytecode.analysis.Frame;
import javassist.bytecode.analysis.Type;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.ToString;

public class ColorlessGraphBuilder {

	@AllArgsConstructor
	@Data
	private static class BlockDataGraph {
		final DataNode[] incLocalNodes;
		final Deque<DataNode> incStackNodes;

		final DataNode[] outLocalNodes;
		final DataNode outReturn;
		final Deque<DataNode> outStackNodes;
	}

	@Data
	private static class BlockDataGraphKey {
		final DataNode[] incLocalNodes;
		final Deque<DataNode> incStackNodes;
		final Node node;
	}

	@Getter
	@Setter
	@ToString
	// not a @Data -- different nodes with same content are different
	private static class DataNode {

		final String description;

		DataNode[] inputs;
		int operation;

		public DataNode(String description) {
			this.description = description;
		}
	}

	static final DataNode CONST_NULL = new DataNode("null");

	static final DataNode CONST_INT_0 = new DataNode("int 0");

	static final DataNode CONST_INT_1 = new DataNode("int 1");

	static final DataNode CONST_LONG_0 = new DataNode("long 0");

	static final DataNode CONST_LONG_1 = new DataNode("long 1");

	private static void assertCompatible(CtClass expected, CtClass actual) throws NotFoundException {
		assert expected != null;
		assert actual != null;
		assert actual.subtypeOf(expected);
	}

	private static void assertCompatible(CtClass expected, Type actual) throws NotFoundException {
		assert actual != null;
		assertCompatible(expected, actual.getCtClass());
	}

	@SneakyThrows
	public void buildGraph(final CtClass ctClass, CtMethod ctMethod) {
		ControlFlow controlFlow = new ControlFlow(ctMethod);

		final Block[] basicBlocks = controlFlow.basicBlocks();
		if (basicBlocks == null || basicBlocks.length == 0) {
			return;
		}

		final Node rootNode = controlFlow.dominatorTree()[0];
		final Block rootBlock = rootNode.block();
		final Frame rootFrame = controlFlow.frameAt(rootBlock.position());

		final DataNode[] incLocalNodes = new DataNode[rootFrame.localsLength()];

		int counter = 0;
		if (!Modifier.isStatic(ctMethod.getModifiers())) {
			assertCompatible(ctMethod.getDeclaringClass(), rootFrame.getLocal(counter));
			incLocalNodes[counter] = new DataNode("this");
			counter += rootFrame.getLocal(counter).getSize();
		}

		final CtClass[] parameterTypes = ctMethod.getParameterTypes();
		for (int i = 0; i < parameterTypes.length; i++) {
			assertCompatible(parameterTypes[i], rootFrame.getLocal(counter));
			incLocalNodes[counter] = new DataNode("arg#" + i + " of " + ctMethod.getLongName());
			counter += rootFrame.getLocal(counter).getSize();
		}

		final Map<BlockDataGraphKey, BlockDataGraph> done = new HashMap<>();
		buildGraphRecursively(ctClass, ctMethod, controlFlow, done, rootNode, incLocalNodes, new LinkedList<>());
	}

	@SneakyThrows
	private BlockDataGraph buildGraphImpl(final CtClass ctClass, final CtMethod ctMethod, final ControlFlow controlFlow,
			final Block block, final DataNode[] incLocalNodes, final Deque<DataNode> incStack) {
		final int firstPos = block.position();
		final int length = block.length();

		final CodeAttribute codeAttribute = ctMethod.getMethodInfo2().getCodeAttribute();
		final ConstPool constPool = codeAttribute.getConstPool();
		final CodeIterator iterator = codeAttribute.iterator();

		final DataNode[] currentLocalNodes = Arrays.copyOf(incLocalNodes, incLocalNodes.length);
		final Deque<DataNode> currentStack = new LinkedList<>(incStack);

		DataNode toReturn = null;

		int index;
		iterator.move(firstPos);

		while (iterator.hasNext() && (index = iterator.next()) < firstPos + length) {
			int op = iterator.byteAt(index);
			switch (op) {
			case Opcode.ACONST_NULL:
				currentStack.push(CONST_NULL);
				break;

			case Opcode.ALOAD:
				currentStack.push(currentLocalNodes[iterator.byteAt(index + 1)]);
				break;

			case Opcode.ALOAD_0:
			case Opcode.ALOAD_1:
			case Opcode.ALOAD_2:
			case Opcode.ALOAD_3:
				currentStack.push(currentLocalNodes[op - Opcode.ALOAD_0]);
				break;

			case Opcode.ARETURN:
				// expected to be last of the instructions
				toReturn = currentStack.pop();
				break;

			case Opcode.ASTORE:
				currentLocalNodes[iterator.byteAt(index + 1)] = currentStack.pop();
				break;

			case Opcode.ASTORE_0:
			case Opcode.ASTORE_1:
			case Opcode.ASTORE_2:
			case Opcode.ASTORE_3:
				currentLocalNodes[op - Opcode.ASTORE_0] = currentStack.pop();
				break;

			case Opcode.ATHROW:
				final DataNode toThrow = currentStack.peek();
				currentStack.clear();
				currentStack.push(toThrow);
				break;

			case Opcode.DUP:
				currentStack.push(currentStack.peek());
				break;

			case Opcode.GETFIELD: {
				final int constantIndex = iterator.u16bitAt(index + 1);
				final String fieldrefName = constPool.getFieldrefName(constantIndex);

				DataNode result = new DataNode("gitfield " + fieldrefName);
				result.inputs = new DataNode[] { currentStack.poll() };

				currentStack.push(result);
			}

			case Opcode.ICONST_0:
				currentStack.push(CONST_INT_0);
				break;
			case Opcode.ICONST_1:
				currentStack.push(CONST_INT_1);
				break;

			case Opcode.IF_ACMPEQ:
			case Opcode.IF_ACMPNE:
			case Opcode.IF_ICMPEQ:
			case Opcode.IF_ICMPGE:
			case Opcode.IF_ICMPGT:
			case Opcode.IF_ICMPLE:
			case Opcode.IF_ICMPLT:
			case Opcode.IF_ICMPNE:
				currentStack.pop();
				currentStack.pop();
				break;

			case Opcode.IFEQ:
			case Opcode.IFGE:
			case Opcode.IFGT:
			case Opcode.IFLE:
			case Opcode.IFLT:
			case Opcode.IFNE:
			case Opcode.IFNONNULL:
			case Opcode.IFNULL:
				currentStack.pop();
				break;

			case Opcode.ILOAD:
				currentStack.push(currentLocalNodes[iterator.byteAt(index + 1)]);
				break;
			case Opcode.ILOAD_0:
			case Opcode.ILOAD_1:
			case Opcode.ILOAD_2:
			case Opcode.ILOAD_3:
				currentStack.push(currentLocalNodes[op - Opcode.ILOAD_0]);
				break;

			case Opcode.INVOKEDYNAMIC:
			case Opcode.INVOKEINTERFACE:
			case Opcode.INVOKESPECIAL:
			case Opcode.INVOKESTATIC:
			case Opcode.INVOKEVIRTUAL: {
				int constantIndex = iterator.u16bitAt(index + 1);
				final ClassPool classPool = ctClass.getClassPool();

//				final String classname = constPool.getMethodrefClassName(constantIndex);
//				final String methodname = constPool.getMethodrefName(constantIndex);
				final String signature = op == Opcode.INVOKEDYNAMIC ? constPool.getInvokeDynamicType(constantIndex)
						: constPool.getMethodrefType(constantIndex);

				CtClass[] params = Descriptor.getParameterTypes(signature, classPool);
				CtClass retType = Descriptor.getReturnType(signature, classPool);

				List<DataNode> inputs = new ArrayList<>();
				if (op != Opcode.INVOKESTATIC && op != Opcode.INVOKEDYNAMIC) {
					// objectref
					inputs.add(currentStack.pop());
				}
				for (int i = 0; i < params.length; i++) {
					inputs.add(currentStack.pop());
				}

				if (!CtPrimitiveType.voidType.equals(retType)) {
					final DataNode dataNode = new DataNode("result of invoke");
					dataNode.inputs = inputs.toArray(new DataNode[inputs.size()]);
					dataNode.operation = op;
					currentStack.push(dataNode);
				}

				break;
			}

			case Opcode.IRETURN:
				toReturn = currentStack.poll();
				break;

			case Opcode.LADD:
			case Opcode.LAND:
			case Opcode.LCMP:
				DataNode value1 = currentStack.peek();
				DataNode value2 = currentStack.peek();
				DataNode result = new DataNode(InstructionPrinter.instructionString(iterator, index, constPool));
				result.inputs = new DataNode[] { value1, value2 };
				result.operation = op;
				currentStack.push(result);
				break;

			case Opcode.LCONST_0:
				currentStack.push(CONST_LONG_0);
				break;
			case Opcode.LCONST_1:
				currentStack.push(CONST_LONG_1);
				break;

			case Opcode.LDC2_W:
				currentStack.push(new DataNode("constant #" + iterator.u16bitAt(index + 1)));
				break;

			case Opcode.LLOAD: {
				final int varIndex = iterator.byteAt(index + 1);
				currentStack.push(currentLocalNodes[varIndex]);
				break;
			}
			case Opcode.LLOAD_0:
			case Opcode.LLOAD_1:
			case Opcode.LLOAD_2:
			case Opcode.LLOAD_3:
				currentStack.push(currentLocalNodes[op - Opcode.LLOAD_0]);
				break;

			case Opcode.LSTORE: {
				final int varIndex = iterator.byteAt(index + 1);
				currentLocalNodes[varIndex] = currentStack.poll();
				break;
			}
			case Opcode.LSTORE_0:
			case Opcode.LSTORE_1:
			case Opcode.LSTORE_2:
			case Opcode.LSTORE_3:
				currentLocalNodes[op - Opcode.LSTORE_0] = currentStack.poll();
				break;

			case Opcode.GOTO:
				// nothing is changed in data
				break;

			case Opcode.LDC:
				currentStack.push(new DataNode("constant #" + iterator.byteAt(index + 1)));
				break;

			case Opcode.NEW:
				currentStack.push(new DataNode("new object reference"));
				break;

			case Opcode.RETURN:
				// return void
				toReturn = null;
				break;

			default:
				throw new UnsupportedOperationException("Unknown opcode: " + Mnemonic.OPCODE[op]);
			}
		}

		return new BlockDataGraph(incLocalNodes, incStack, currentLocalNodes, toReturn, currentStack);
	}

	@SneakyThrows
	private void buildGraphRecursively(final CtClass ctClass, final CtMethod ctMethod, final ControlFlow controlFlow,
			Map<BlockDataGraphKey, BlockDataGraph> done, final Node node, final DataNode[] incLocalNodes,
			final Deque<DataNode> incStackNodes) {

		final BlockDataGraphKey key = new BlockDataGraphKey(incLocalNodes, incStackNodes, node);
		if (done.containsKey(key)) {
			return;
		}

		BlockDataGraph blockGraph = buildGraphImpl(ctClass, ctMethod, controlFlow, node.block(), incLocalNodes,
				incStackNodes);
		done.put(key, blockGraph);

		for (int c = 0; c < node.children(); c++) {
			buildGraphRecursively(ctClass, ctMethod, controlFlow, done, node.child(c), blockGraph.outLocalNodes,
					blockGraph.outStackNodes);
		}
	}

}
