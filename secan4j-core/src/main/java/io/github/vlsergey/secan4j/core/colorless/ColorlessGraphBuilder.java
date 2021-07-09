package io.github.vlsergey.secan4j.core.colorless;

import static java.util.Collections.unmodifiableCollection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;

import javassist.ClassPool;
import javassist.CtBehavior;
import javassist.CtClass;
import javassist.CtField;
import javassist.Modifier;
import javassist.NotFoundException;
import javassist.bytecode.BadBytecode;
import javassist.bytecode.CodeAttribute;
import javassist.bytecode.CodeIterator;
import javassist.bytecode.ConstPool;
import javassist.bytecode.Descriptor;
import javassist.bytecode.InstructionPrinter;
import javassist.bytecode.Mnemonic;
import javassist.bytecode.Opcode;
import javassist.bytecode.SignatureAttribute;
import javassist.bytecode.SignatureAttribute.MethodSignature;
import javassist.bytecode.analysis.ControlFlow;
import javassist.bytecode.analysis.ControlFlow.Block;
import javassist.bytecode.analysis.ControlFlow.Node;
import javassist.bytecode.analysis.Frame;
import javassist.bytecode.analysis.Type;
import lombok.Data;
import lombok.NonNull;
import lombok.SneakyThrows;

public class ColorlessGraphBuilder {

	@Data
	private static class BlockDataGraphKey {
		final Block block;
		final DataNode[] incLocalNodes;
		final Collection<DataNode> incStackNodes;
	}

	private interface Heap {
		DataNode getField(DataNode ref, String fieldRef);

		void putField(DataNode ref, String fieldRef, DataNode value);
	}

	static final DataNode CONST_INT_0 = new DataNode("int 0").setType(Type.INTEGER);
	static final DataNode CONST_INT_1 = new DataNode("int 1").setType(Type.INTEGER);
	static final DataNode CONST_INT_2 = new DataNode("int 2").setType(Type.INTEGER);
	static final DataNode CONST_INT_3 = new DataNode("int 3").setType(Type.INTEGER);
	static final DataNode CONST_INT_4 = new DataNode("int 4").setType(Type.INTEGER);
	static final DataNode CONST_INT_5 = new DataNode("int 5").setType(Type.INTEGER);

	static final DataNode[] CONST_INTS = new DataNode[] { CONST_INT_0, CONST_INT_1, CONST_INT_2, CONST_INT_3,
			CONST_INT_4, CONST_INT_5 };

	static final DataNode CONST_LONG_0 = new DataNode("long 0").setType(Type.LONG);

	static final DataNode CONST_LONG_1 = new DataNode("long 1").setType(Type.LONG);

	static final DataNode CONST_NULL = new DataNode("null").setType(Type.UNINIT);

	private static final LinkedList<DataNode> EMPTY_STACK = new LinkedList<>();

	// TODO: move to configuration
	private static final int MAX_SINGLE_BLOCK_ENTERS = 5;

	// TODO: replace with slf4j?
	private static boolean TRACE = false;

	private static void assertAssignableFrom(CtClass that, Type assignableFrom) throws NotFoundException {
		assert that != null;
		assert assignableFrom != null;

		assert Type.get(that).isAssignableFrom(assignableFrom)
				: "Class " + that + " not assignable from " + assignableFrom;
	}

	private static void assertAssignableFrom(Type that, CtClass assignableFrom) throws NotFoundException {
		assert that != null;
		assert assignableFrom != null;

		if (that.getCtClass().isPrimitive()) {
			assert assignableFrom.isPrimitive() && that.getSize() >= Type.get(assignableFrom).getSize();
			return;
		}

		assert that.isAssignableFrom(Type.get(assignableFrom))
				: "Class " + that + " not assignable from " + assignableFrom;
	}

	static void assertIntAlike(DataNode dataNode) {
		final Type actual = dataNode.type;
		assert actual == Type.BYTE || actual == Type.CHAR || actual == Type.INTEGER
				: "Expected int-alike, but found " + actual;
	}

	static void assertSameSizeOnFrameStack(Deque<DataNode> actual, Frame expected) {
		if (actual.isEmpty()) {
			assert expected.getTopIndex() == -1;
			return;
		}

		int topIndex = expected.getTopIndex();
		assert actual.stream().map(DataNode::getType).mapToInt(Type::getSize).sum() == (topIndex + 1)
				: "Size of DataNode stack is " + actual.stream().map(DataNode::getType).mapToInt(Type::getSize).sum()
						+ ", but expected size was " + (topIndex + 1);
	}

	static void assertSameTypeOnFrameLocals(DataNode[] actual, Frame expected) {
		for (int i = 0; i < actual.length; i++) {
			DataNode dataNode = actual[i];
			if (dataNode == null) {
				// dataNode is not assigned...
				// it may be assigned later or it's part of "wide" type in another cell
			} else {
				assert (dataNode.getType() == expected.getLocal(i))
						|| (expected.getLocal(i).getCtClass().isPrimitive()
								&& dataNode.getType().getCtClass().isPrimitive())
						|| expected.getLocal(i).isAssignableFrom(dataNode.getType());
			}
		}
	}

	private static <T> T[] collect(Collection<BlockDataGraph> collectFrom, Function<BlockDataGraph, T[]> accessor,
			IntFunction<T[]> arrayGenerator) {
		return collectFrom.stream().flatMap(graph -> Arrays.stream(accessor.apply(graph))).distinct()
				.toArray(arrayGenerator);
	}

	private static Type getMergedType(DataNode[] dataNodes) {
		Type result = dataNodes[0].type;
		for (int i = 1; i < dataNodes.length; i++) {
			result = result.merge(dataNodes[i].type);
		}
		return result;
	}

	private static void processInstructionWithStackOnly(final @NonNull ConstPool constPool,
			final @NonNull CodeIterator iterator, final int index, final @NonNull Deque<DataNode> currentStack,
			final int toPoll, final @NonNull Supplier<Type> typeOfNextStackTop) {
		processInstructionWithStackOnly(currentStack, iterator.byteAt(index), toPoll, typeOfNextStackTop,
				() -> new DataNode(InstructionPrinter.instructionString(iterator, index, constPool)));
	}

	private static void processInstructionWithStackOnly(final @NonNull Deque<DataNode> currentStack, final int op,
			final int toPoll, final @NonNull Supplier<Type> typeOfNextStackTop, Supplier<DataNode> resultTypeSupplier) {
		DataNode result = resultTypeSupplier.get().setOperation(op).setType(typeOfNextStackTop.get());
		DataNode[] inputs = new DataNode[toPoll];
		for (int i = 0; i < toPoll; i++) {
			inputs[i] = currentStack.pop();
		}
		result.inputs = inputs;
		currentStack.push(result);
	}

	@SneakyThrows
	public @NonNull Optional<BlockDataGraph> buildGraph(final CtClass ctClass, final @NonNull CtBehavior ctMethod) {
		ControlFlow controlFlow = new ControlFlow(ctClass, ctMethod.getMethodInfo());

		final Block[] basicBlocks = controlFlow.basicBlocks();
		if (basicBlocks == null || basicBlocks.length == 0) {
			return Optional.empty();
		}

		final Node rootNode = controlFlow.dominatorTree()[0];
		final Block rootBlock = rootNode.block();
		final Frame rootFrame = controlFlow.frameAt(rootBlock.position());

		final DataNode[] incLocalNodes = new DataNode[rootFrame.localsLength()];

		List<DataNode> methodParams = new ArrayList<>();

		// TODO: push down to analyzed class?
		int counter = 0;
		if (!Modifier.isStatic(ctMethod.getModifiers())) {
			assertAssignableFrom(ctMethod.getDeclaringClass(), rootFrame.getLocal(counter));
			methodParams.add(incLocalNodes[counter] = new DataNode("this").setType(Type.get(ctClass)));
			counter += rootFrame.getLocal(counter).getSize();
		}

		final CtClass[] parameterTypes = ctMethod.getParameterTypes();
		for (int i = 0; i < parameterTypes.length; i++) {
			assertAssignableFrom(rootFrame.getLocal(counter), parameterTypes[i]);
			methodParams.add(incLocalNodes[counter] = new MethodParameterNode(ctClass, ctMethod, i));
			counter += rootFrame.getLocal(counter).getSize();
		}

		final Map<BlockDataGraphKey, BlockDataGraph> done = new LinkedHashMap<>();
		final int[] blockEnters = new int[basicBlocks.length];
		buildGraphRecursively(ctClass, ctMethod, controlFlow, done, rootNode.block(), incLocalNodes, EMPTY_STACK,
				blockEnters);

		if (TRACE) {
			System.out.println("Block enters: " + Arrays.toString(blockEnters));
		}

		final Collection<BlockDataGraph> doneGraphs = done.values();
		DataNode[] allNodes = collect(doneGraphs, BlockDataGraph::getAllNodes, DataNode[]::new);
		final DataNode[] outputs = collect(doneGraphs, BlockDataGraph::getOutReturns, DataNode[]::new);
		final Invocation[] invokations = collect(doneGraphs, BlockDataGraph::getInvokations, Invocation[]::new);

		final DataNode[] methodReturnNodes;
		if (outputs.length < 2)
			methodReturnNodes = outputs;
		else {
			final DataNode compositionNode = new AnyOfNode().setInputs(outputs).setType(getMergedType(outputs));
			methodReturnNodes = new DataNode[] { compositionNode };

			allNodes = Arrays.copyOf(allNodes, allNodes.length + 1);
			allNodes[allNodes.length - 1] = compositionNode;
		}

		final PutFieldNode[] putFieldNodes = collect(doneGraphs, BlockDataGraph::getPutFieldNodes, PutFieldNode[]::new);
		final PutStaticNode[] putStaticNodes = collect(doneGraphs, BlockDataGraph::getPutStaticNodes,
				PutStaticNode[]::new);

		return Optional.of(new BlockDataGraph(allNodes, incLocalNodes, EMPTY_STACK, invokations,
				methodParams.toArray(DataNode[]::new), methodReturnNodes, DataNode.EMPTY_DATA_NODES, outputs,
				EMPTY_STACK, putFieldNodes, putStaticNodes));
	}

	@SneakyThrows
	private BlockDataGraph buildGraphImpl(final @NonNull CtClass ctClass, final @NonNull CtBehavior ctMethod,
			final @NonNull ControlFlow controlFlow, final @NonNull Block block, final @NonNull DataNode[] incLocalNodes,
			final @NonNull Deque<DataNode> incStack) {
		final int firstPos = block.position();
		final int length = block.length();

		final CodeAttribute codeAttribute = ctMethod.getMethodInfo2().getCodeAttribute();
		final ClassPool classPool = ctClass.getClassPool();
		final ConstPool constPool = codeAttribute.getConstPool();
		final CodeIterator iterator = codeAttribute.iterator();

		final DataNode[] currentLocals = Arrays.copyOf(incLocalNodes, incLocalNodes.length);
		final Deque<DataNode> currentStack = new LinkedList<>(incStack);

		DataNode toReturn = null;
		List<Invocation> invokations = new ArrayList<>();
		List<PutFieldNode> putFieldNodes = new ArrayList<>();
		List<PutStaticNode> putStaticNodes = new ArrayList<>();

		final Set<DataNode> allNodesSet = new LinkedHashSet<>();
		allNodesSet.addAll(currentStack);
		allNodesSet.addAll(Arrays.asList(currentLocals));

		int index;
		iterator.move(firstPos);

		if (TRACE) {
			System.out.println();
			System.out.println("Method: " + ctMethod.getLongName());
			System.out.println("Block: " + block);

			final Frame frame = controlFlow.frameAt(block.position());
			System.out.println("Frame: " + frame);
			System.out.println();

			System.out.println("DN Stack: ");
			currentStack.forEach(dn -> System.out.println(" * " + dn));
			System.out.println("DN Locals: ");
			Arrays.asList(currentLocals).forEach(dn -> System.out.println(" * " + dn));

		}

		assertSameTypeOnFrameLocals(currentLocals, controlFlow.frameAt(block.position()));
		assertSameSizeOnFrameStack(currentStack, controlFlow.frameAt(block.position()));

		while (iterator.hasNext() && (index = iterator.next()) < firstPos + length) {
			if (TRACE) {
				System.out.println(index + ":\t" + InstructionPrinter.instructionString(iterator, index, constPool));
				System.out.println(controlFlow.frameAt(index));
				System.out.println("* Before: ");
				System.out.println("* * Locals: ");
				Arrays.asList(currentLocals).forEach(dn -> System.out.println("* * * " + dn));
				System.out.println("* * Stack: ");
				currentStack.forEach(dn -> System.out.println("* * * " + dn));
			}

			assertSameTypeOnFrameLocals(currentLocals, controlFlow.frameAt(index));
			assertSameSizeOnFrameStack(currentStack, controlFlow.frameAt(index));

			toReturn = processInstruction(classPool, constPool, new Heap() {

				@Override
				public DataNode getField(DataNode ref, String fieldRef) {
					throw new UnsupportedOperationException("NYI");
				}

				@Override
				public void putField(DataNode ref, String fieldRef, DataNode value) {
					throw new UnsupportedOperationException("NYI");
				}

			}, currentLocals, currentStack, toReturn, invokations, putFieldNodes, putStaticNodes, iterator, index,
					() -> {
						try {
							final Frame nextInstructionFrame = controlFlow.frameAt(iterator.lookAhead());
							final int topIndex = nextInstructionFrame.getTopIndex();
							final Type onTopOfStack = nextInstructionFrame.getStack(topIndex);
							if (onTopOfStack == Type.TOP) {
								final Type beforeTop = nextInstructionFrame.getStack(topIndex - 1);
								assert beforeTop.getSize() == 2;
								return beforeTop;
							}
							return onTopOfStack;
						} catch (BadBytecode exc) {
							throw new RuntimeException(exc);
						}
					});

			allNodesSet.addAll(currentStack);
			allNodesSet.addAll(Arrays.asList(currentLocals));

			int nextIndex = iterator.lookAhead();
			if (nextIndex < firstPos + length) {
				final Frame nextFrame = controlFlow.frameAt(nextIndex);

				if (TRACE) {
					System.out.println("* After: ");
					System.out.println("* " + nextFrame);
					System.out.println("* * Locals: ");
					Arrays.asList(currentLocals).forEach(dn -> System.out.println("* * * " + dn));
					System.out.println("* * Stack: ");
					currentStack.forEach(dn -> System.out.println("* * * " + dn));
					System.out.println();
				}

				assertSameTypeOnFrameLocals(currentLocals, nextFrame);
				assertSameSizeOnFrameStack(currentStack, nextFrame);
			}
		}

		allNodesSet.remove(null);

		return new BlockDataGraph(allNodesSet.toArray(DataNode[]::new), incLocalNodes, incStack,
				invokations.toArray(Invocation[]::new), DataNode.EMPTY_DATA_NODES, DataNode.EMPTY_DATA_NODES,
				currentLocals, toReturn == null ? DataNode.EMPTY_DATA_NODES : new DataNode[] { toReturn }, currentStack,
				putFieldNodes.toArray(PutFieldNode[]::new), putStaticNodes.toArray(PutStaticNode[]::new));
	}

	@SneakyThrows
	private void buildGraphRecursively(final @NonNull CtClass ctClass, final @NonNull CtBehavior ctMethod,
			final @NonNull ControlFlow controlFlow, final @NonNull Map<BlockDataGraphKey, BlockDataGraph> done,
			final @NonNull Block block, final @NonNull DataNode[] incLocalNodes,
			final @NonNull Deque<DataNode> incStackNodes, final @NonNull int[] blockEnters) {

		if (++blockEnters[block.index()] > MAX_SINGLE_BLOCK_ENTERS) {
			return;
		}

		final BlockDataGraphKey key = new BlockDataGraphKey(block, incLocalNodes,
				unmodifiableCollection(incStackNodes));
		if (done.containsKey(key)) {
			return;
		}

		BlockDataGraph blockGraph = buildGraphImpl(ctClass, ctMethod, controlFlow, block, incLocalNodes, incStackNodes);
		done.put(key, blockGraph);

		for (int e = 0; e < block.exits(); e++) {
			Block exitBlock = block.exit(e);
			buildGraphRecursively(ctClass, ctMethod, controlFlow, done, exitBlock, blockGraph.getOutLocalNodes(),
					blockGraph.getOutStackNodes(), blockEnters);
		}

	}

	private Type getType(final ClassPool classPool, final ConstPool constPool, int tagIndex)
			throws NotFoundException, BadBytecode {
		int tag = constPool.getTag(tagIndex);
		Type type;
		switch (tag) {
		case ConstPool.CONST_String:
			type = Type.get(classPool.get("java.lang.String"));
			break;
		case ConstPool.CONST_Integer:
			type = Type.INTEGER;
			break;
		case ConstPool.CONST_Float:
			type = Type.FLOAT;
			break;
		case ConstPool.CONST_Long:
			type = Type.LONG;
			break;
		case ConstPool.CONST_Double:
			type = Type.DOUBLE;
			break;
		case ConstPool.CONST_Class:
			type = Type.get(classPool.get("java.lang.Class"));
			break;
		default:
			throw new BadBytecode("bad LDC [pos = " + tagIndex + "]: " + tag);
		}
		return type;
	}

	// TODO: extract to class with fields... too many arguments
	@SneakyThrows
	private DataNode processInstruction(final ClassPool classPool, final ConstPool constPool, final Heap heap,
			final DataNode[] currentLocals, final Deque<DataNode> currentStack, DataNode toReturn,
			List<Invocation> invokations, List<PutFieldNode> putFieldNodes, List<PutStaticNode> putStaticNodes,
			final CodeIterator iterator, int index, Supplier<Type> typeOfNextStackTop) {

		int op = iterator.byteAt(index);
		switch (op) {
		case Opcode.ACONST_NULL:
			currentStack.push(CONST_NULL);
			break;

		case Opcode.ALOAD:
			currentStack.push(currentLocals[iterator.byteAt(index + 1)]);
			break;

		case Opcode.ALOAD_0:
		case Opcode.ALOAD_1:
		case Opcode.ALOAD_2:
		case Opcode.ALOAD_3:
			currentStack.push(currentLocals[op - Opcode.ALOAD_0]);
			break;

		case Opcode.ARETURN:
			// expected to be last of the instructions
			toReturn = currentStack.pop();
			break;

		case Opcode.ARRAYLENGTH:
			processInstructionWithStackOnly(constPool, iterator, index, currentStack, 1, typeOfNextStackTop);
			break;

		case Opcode.ASTORE:
			currentLocals[iterator.byteAt(index + 1)] = currentStack.pop();
			break;

		case Opcode.ASTORE_0:
		case Opcode.ASTORE_1:
		case Opcode.ASTORE_2:
		case Opcode.ASTORE_3:
			currentLocals[op - Opcode.ASTORE_0] = currentStack.pop();
			break;

		case Opcode.ATHROW:
			final DataNode toThrow = currentStack.peek();
			currentStack.clear();
			currentStack.push(toThrow);
			break;

		case Opcode.BALOAD:
		case Opcode.CALOAD:
		case Opcode.DALOAD:
		case Opcode.FALOAD:
		case Opcode.IALOAD:
		case Opcode.LALOAD:
		case Opcode.SALOAD: {
			processInstructionWithStackOnly(constPool, iterator, index, currentStack, 2, typeOfNextStackTop);
			break;
		}

		case Opcode.BASTORE:
		case Opcode.CASTORE:
		case Opcode.DASTORE:
		case Opcode.FASTORE:
		case Opcode.IASTORE:
		case Opcode.LASTORE:
		case Opcode.SASTORE:
			currentStack.pop();
			currentStack.pop();
			currentStack.pop();
			break;

		case Opcode.BIPUSH:
			processInstructionWithStackOnly(constPool, iterator, index, currentStack, 0, typeOfNextStackTop);
			break;

		case Opcode.CHECKCAST:
			processInstructionWithStackOnly(constPool, iterator, index, currentStack, 1, typeOfNextStackTop);
			break;

		case Opcode.DLOAD: {
			final int varIndex = iterator.byteAt(index + 1);
			currentStack.push(currentLocals[varIndex]);
			break;
		}
		case Opcode.DLOAD_0:
		case Opcode.DLOAD_1:
		case Opcode.DLOAD_2:
		case Opcode.DLOAD_3:
			currentStack.push(currentLocals[op - Opcode.DLOAD_0]);
			break;

		case Opcode.DSTORE: {
			final int varIndex = iterator.byteAt(index + 1);
			currentLocals[varIndex] = currentStack.pop();
			break;
		}
		case Opcode.DSTORE_0:
		case Opcode.DSTORE_1:
		case Opcode.DSTORE_2:
		case Opcode.DSTORE_3:
			currentLocals[op - Opcode.DSTORE_0] = currentStack.pop();
			break;

		case Opcode.DUP:
			currentStack.push(currentStack.peek());
			break;

		case Opcode.FLOAD:
			currentStack.push(currentLocals[iterator.byteAt(index + 1)]);
			assert currentStack.peek().type == Type.FLOAT;
			break;

		case Opcode.FLOAD_0:
		case Opcode.FLOAD_1:
		case Opcode.FLOAD_2:
		case Opcode.FLOAD_3:
			currentStack.push(currentLocals[op - Opcode.FLOAD_0]);
			assert currentStack.peek().type == Type.FLOAT;
			break;

		case Opcode.GETFIELD:
		case Opcode.GETSTATIC: {
			int constantIndex = iterator.u16bitAt(index + 1);
			final String fieldrefClassName = constPool.getFieldrefClassName(constantIndex);
			final String fieldrefName = constPool.getFieldrefName(constantIndex);
			final String fieldrefType = constPool.getFieldrefType(constantIndex);

			final CtClass fieldClass = classPool.get(fieldrefClassName);
			final CtField ctField = fieldClass.getField(fieldrefName, fieldrefType);

			// XXX: USE HEAP
			if (op == Opcode.GETFIELD) {
				processInstructionWithStackOnly(currentStack, op, 1, typeOfNextStackTop,
						() -> new GetFieldNode(fieldClass, ctField));
			} else {
				processInstructionWithStackOnly(currentStack, op, 0, typeOfNextStackTop,
						() -> new GetStaticNode(fieldClass, ctField));
			}
			break;
		}

		case Opcode.GOTO:
			// nothing is changed in data
			break;

		case Opcode.I2B:
		case Opcode.I2C:
		case Opcode.I2D:
		case Opcode.I2F:
		case Opcode.I2L:
		case Opcode.I2S:
			processInstructionWithStackOnly(constPool, iterator, index, currentStack, 1, typeOfNextStackTop);
			break;

		case Opcode.IADD:
		case Opcode.IAND:
		case Opcode.IDIV:
		case Opcode.IMUL:
		case Opcode.IOR:
		case Opcode.IREM:
		case Opcode.ISHL:
		case Opcode.ISHR:
		case Opcode.ISUB:
		case Opcode.IUSHR:
		case Opcode.IXOR:
			processInstructionWithStackOnly(constPool, iterator, index, currentStack, 2, typeOfNextStackTop);
			break;

		case Opcode.ICONST_0:
		case Opcode.ICONST_1:
		case Opcode.ICONST_2:
		case Opcode.ICONST_3:
		case Opcode.ICONST_4:
		case Opcode.ICONST_5:
			currentStack.push(CONST_INTS[op - Opcode.ICONST_0]);
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

		case Opcode.IINC: {
			DataNode prevValue = currentLocals[iterator.byteAt(index + 1)];
			DataNode nextValue = new DataNode(InstructionPrinter.instructionString(iterator, index, constPool))
					.setInputs(new DataNode[] { prevValue }).setOperation(op).setType(prevValue.getType());
			currentLocals[iterator.byteAt(index + 1)] = nextValue;
			break;
		}

		case Opcode.ILOAD:
			currentStack.push(currentLocals[iterator.byteAt(index + 1)]);
			break;
		case Opcode.ILOAD_0:
		case Opcode.ILOAD_1:
		case Opcode.ILOAD_2:
		case Opcode.ILOAD_3:
			currentStack.push(currentLocals[op - Opcode.ILOAD_0]);
			break;

		case Opcode.INEG:
		case Opcode.INSTANCEOF: {
			processInstructionWithStackOnly(constPool, iterator, index, currentStack, 1, typeOfNextStackTop);
			break;
		}

		case Opcode.INVOKEDYNAMIC: {
			int constantIndex = iterator.u16bitAt(index + 1);

			final int nameAndType = constPool.getInvokeDynamicNameAndType(constantIndex);
			final String signature = constPool.getUtf8Info(constPool.getNameAndTypeDescriptor(nameAndType));

			final MethodSignature methodSignature = SignatureAttribute.toMethodSignature(signature);
			processInstructionWithStackOnly(constPool, iterator, index, currentStack,
					methodSignature.getParameterTypes().length, typeOfNextStackTop);
			break;
		}

		case Opcode.INVOKEINTERFACE:
		case Opcode.INVOKESPECIAL:
		case Opcode.INVOKESTATIC:
		case Opcode.INVOKEVIRTUAL: {
			int constantIndex = iterator.u16bitAt(index + 1);

			String className = null;
			String methodName = null;
			String signature = null;

			/*
			 * invokes an interface method on object objectref and puts the result on the
			 * stack (might be void); the interface method is identified by method reference
			 * index in constant pool (indexbyte1 << 8 | indexbyte2)
			 */
			if (op == Opcode.INVOKEINTERFACE || op == Opcode.INVOKESPECIAL || op == Opcode.INVOKEVIRTUAL
					|| op == Opcode.INVOKESTATIC) {
				className = constPool.getMethodrefClassName(constantIndex);
				methodName = constPool.getMethodrefName(constantIndex);
				signature = constPool.getMethodrefType(constantIndex);
			} else {
				final int nameAndType = constPool.getInvokeDynamicNameAndType(constantIndex);
				methodName = constPool.getUtf8Info(constPool.getNameAndTypeName(nameAndType));
				signature = constPool.getUtf8Info(constPool.getNameAndTypeDescriptor(nameAndType));
			}

			CtClass[] params = Descriptor.getParameterTypes(signature, classPool);
			CtClass retType = Descriptor.getReturnType(signature, classPool);

			List<DataNode> inputs = new ArrayList<>();
			if (op != Opcode.INVOKESTATIC) {
				// objectref
				inputs.add(currentStack.pop());
			}
			for (int i = 0; i < params.length; i++) {
				inputs.add(currentStack.pop());
			}
			Collections.reverse(inputs);
			final DataNode[] inputsArray = inputs.toArray(new DataNode[inputs.size()]);

			DataNode result = null;
			if (!CtClass.voidType.equals(retType)) {
				result = new DataNode("result of invoke");
				result.inputs = inputsArray;
				result.operation = op;
				result.type = Type.get(retType);
				currentStack.push(result);
			}

			invokations.add(new Invocation(className, methodName, signature, inputsArray,
					result == null ? DataNode.EMPTY_DATA_NODES : new DataNode[] { result }, op == Opcode.INVOKESTATIC));
			break;
		}

		case Opcode.IRETURN: {
			toReturn = currentStack.pop();
			break;
		}

		case Opcode.ISTORE:
			currentLocals[iterator.byteAt(index + 1)] = currentStack.pop();
			break;

		case Opcode.ISTORE_0:
		case Opcode.ISTORE_1:
		case Opcode.ISTORE_2:
		case Opcode.ISTORE_3:
			currentLocals[op - Opcode.ISTORE_0] = currentStack.pop();
			break;

		case Opcode.LADD:
		case Opcode.LAND:
		case Opcode.LCMP:
			processInstructionWithStackOnly(constPool, iterator, index, currentStack, 2, typeOfNextStackTop);
			break;

		case Opcode.LCONST_0:
			currentStack.push(CONST_LONG_0);
			break;
		case Opcode.LCONST_1:
			currentStack.push(CONST_LONG_1);
			break;

		case Opcode.LDC:
		case Opcode.LDC_W: {
			int tagIndex = op == Opcode.LDC ? iterator.byteAt(index + 1) : iterator.u16bitAt(index + 1);
			String description = "constant #" + tagIndex;

			Type type = getType(classPool, constPool, tagIndex);
			if (type.getCtClass().getName().equals("java.lang.String")) {
				description = "\"" + constPool.getStringInfo(tagIndex) + "\"";
			}

			currentStack.push(new DataNode(description).setType(type));
			break;
		}

		case Opcode.LDC2_W: {
			int tagIndex = iterator.u16bitAt(index + 1);
			String description = "constant #" + tagIndex;

			Type type = getType(classPool, constPool, tagIndex);
			if (type.getCtClass().getName().equals("java.lang.String")) {
				description = "\"" + constPool.getUtf8Info(index) + "\"";
			}

			currentStack.push(new DataNode(description).setType(type));
			break;
		}

		case Opcode.LLOAD: {
			final int varIndex = iterator.byteAt(index + 1);
			currentStack.push(currentLocals[varIndex]);
			break;
		}
		case Opcode.LLOAD_0:
		case Opcode.LLOAD_1:
		case Opcode.LLOAD_2:
		case Opcode.LLOAD_3:
			currentStack.push(currentLocals[op - Opcode.LLOAD_0]);
			break;

		case Opcode.LSTORE: {
			final int varIndex = iterator.byteAt(index + 1);
			currentLocals[varIndex] = currentStack.pop();
			break;
		}
		case Opcode.LSTORE_0:
		case Opcode.LSTORE_1:
		case Opcode.LSTORE_2:
		case Opcode.LSTORE_3:
			currentLocals[op - Opcode.LSTORE_0] = currentStack.pop();
			break;

		case Opcode.NEW:
			processInstructionWithStackOnly(constPool, iterator, index, currentStack, 0, typeOfNextStackTop);
			break;
		case Opcode.NEWARRAY:
			processInstructionWithStackOnly(constPool, iterator, index, currentStack, 1, typeOfNextStackTop);
			break;

		case Opcode.POP: {
			DataNode removed = currentStack.pop();
			assert removed.getType().getSize() == 1;
			break;
		}

		case Opcode.POP2: {
			DataNode removed = currentStack.pop();
			if (removed.getType().getSize() != 2) {
				DataNode removed2 = currentStack.pop();
				assert removed2.getType().getSize() == 1;
			}
			break;
		}

		case Opcode.PUTFIELD:
		case Opcode.PUTSTATIC: {
			int constantIndex = iterator.u16bitAt(index + 1);
			final String fieldrefClassName = constPool.getFieldrefClassName(constantIndex);
			final String fieldrefName = constPool.getFieldrefName(constantIndex);
			final String fieldrefType = constPool.getFieldrefType(constantIndex);

			final CtClass fieldClass = classPool.get(fieldrefClassName);
			final CtField ctField = fieldClass.getField(fieldrefName, fieldrefType);

			// XXX: USE HEAP
			if (op == Opcode.PUTFIELD) {
				putFieldNodes.add(new PutFieldNode(fieldClass, ctField, currentStack.pop(), currentStack.pop()));
			} else {
				putStaticNodes.add(new PutStaticNode(fieldClass, ctField, currentStack.pop()));
			}
			break;
		}

		case Opcode.SIPUSH:
			int constantValue = iterator.u16bitAt(index + 1);
			currentStack.push(new DataNode("shoart as int " + constantValue).setOperation(op).setType(Type.INTEGER));
			break;

		case Opcode.RETURN:
			// return void
			toReturn = null;
			break;

		default:
			throw new UnsupportedOperationException("Unknown opcode: " + Mnemonic.OPCODE[op]);
		}
		return toReturn;
	}

}
