package io.github.vlsergey.secan4j.core.colorless;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtField;
import javassist.bytecode.BadBytecode;
import javassist.bytecode.CodeAttribute;
import javassist.bytecode.CodeIterator;
import javassist.bytecode.ConstPool;
import javassist.bytecode.Descriptor;
import javassist.bytecode.InstructionPrinter;
import javassist.bytecode.MethodInfo;
import javassist.bytecode.Mnemonic;
import javassist.bytecode.Opcode;
import javassist.bytecode.SignatureAttribute;
import javassist.bytecode.SignatureAttribute.MethodSignature;
import javassist.bytecode.analysis.ControlFlow;
import javassist.bytecode.analysis.ControlFlow.Block;
import javassist.bytecode.analysis.Frame;
import javassist.bytecode.analysis.Type;
import lombok.Getter;
import lombok.NonNull;
import lombok.SneakyThrows;

@Getter
public class ColorlessBlockGraphBuilder {

	private interface Heap {
		DataNode getField(DataNode ref, String fieldRef);

		void putField(DataNode ref, String fieldRef, DataNode value);
	}

	private static final Heap HEAP = new Heap() {

		@Override
		public DataNode getField(DataNode ref, String fieldRef) {
			throw new UnsupportedOperationException("NYI");
		}

		@Override
		public void putField(DataNode ref, String fieldRef, DataNode value) {
			throw new UnsupportedOperationException("NYI");
		}

	};

	// TODO: replace with slf4j?
	private static boolean TRACE = false;

	private final @NonNull Block block;
	private final @NonNull CtClass ctClass;
	private final @NonNull ClassPool classPool;
	private int currentIndex = -1;
	private final DataNode[] currentLocals;
	private final Deque<DataNode> currentStack;
	private final @NonNull DataNodeFactory dataNodeFactory;
	private final @NonNull DataNode[] incLocalNodes;
	private final @NonNull Deque<DataNode> incStack;
	private final CodeAttribute methodCodeAttribute;
	private final CodeIterator methodCodeIterator;
	private final ConstPool methodConstPool;
	private final @NonNull ControlFlow methodControlFlow;
	private final @NonNull MethodInfo methodInfo;
	private final Set<DataNode> setOfAllNodes = new LinkedHashSet<>();

	public ColorlessBlockGraphBuilder(final @NonNull ClassPool classPool, final @NonNull CtClass ctClass,
			final @NonNull MethodInfo methodInfo, final @NonNull ControlFlow methodControlFlow,
			final @NonNull Block block, final @NonNull DataNode[] incLocalNodes,
			final @NonNull Deque<DataNode> incStack) {
		super();
		this.classPool = classPool;
		this.ctClass = ctClass;
		this.methodInfo = methodInfo;

		this.methodCodeAttribute = methodInfo.getCodeAttribute();
		if (methodCodeAttribute != null) {
			this.methodCodeIterator = methodCodeAttribute.iterator();
			this.methodConstPool = methodCodeAttribute.getConstPool();
		} else {
			this.methodCodeIterator = null;
			this.methodConstPool = null;
		}
		this.methodControlFlow = methodControlFlow;

		this.block = block;
		this.incLocalNodes = incLocalNodes;
		this.incStack = incStack;

		this.currentLocals = Arrays.copyOf(incLocalNodes, incLocalNodes.length);
		this.currentStack = new LinkedList<>(incStack);

		this.dataNodeFactory = new DataNodeFactory(this, new NodeCollectors(setOfAllNodes::add));
		Arrays.stream(this.currentLocals).filter(Objects::nonNull).forEach(setOfAllNodes::add);
		this.currentStack.stream().filter(Objects::nonNull).forEach(setOfAllNodes::add);
	}

	private void assertSameSizeOnFrameStack(Frame expected) {
		if (currentStack.isEmpty()) {
			assert expected.getTopIndex() == -1;
			return;
		}

		int topIndex = expected.getTopIndex();
		assert currentStack.stream().map(DataNode::getType).mapToInt(Type::getSize).sum() == (topIndex + 1)
				: "Size of DataNode stack is "
						+ currentStack.stream().map(DataNode::getType).mapToInt(Type::getSize).sum()
						+ ", but expected size was " + (topIndex + 1);
	}

	private void assertSameTypeOnFrameLocals(Frame expected) {
		for (int i = 0; i < currentLocals.length; i++) {
			DataNode dataNode = currentLocals[i];
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

	@SneakyThrows
	public BlockDataGraph buildGraph() {
		final int firstPos = block.position();
		final int length = block.length();

		DataNode toReturn = null;
		List<Invocation> invokations = new ArrayList<>();
		List<PutFieldNode> putFieldNodes = new ArrayList<>();
		List<PutStaticNode> putStaticNodes = new ArrayList<>();

		methodCodeIterator.move(firstPos);

		if (TRACE) {
			System.out.println();
			System.out.println("Block: " + block);

			final Frame frame = methodControlFlow.frameAt(block.position());
			System.out.println("Frame: " + frame);
			System.out.println();

			System.out.println("DN Stack: ");
			currentStack.forEach(dn -> System.out.println(" * " + dn));
			System.out.println("DN Locals: ");
			Arrays.asList(currentLocals).forEach(dn -> System.out.println(" * " + dn));

		}

		assertSameTypeOnFrameLocals(methodControlFlow.frameAt(block.position()));
		assertSameSizeOnFrameStack(methodControlFlow.frameAt(block.position()));

		while (methodCodeIterator.hasNext() && (currentIndex = methodCodeIterator.next()) < firstPos + length) {
			if (TRACE) {
				System.out.println(currentIndex + ":\t"
						+ InstructionPrinter.instructionString(methodCodeIterator, currentIndex, methodConstPool));
				System.out.println(methodControlFlow.frameAt(currentIndex));
				System.out.println("* Before: ");
				System.out.println("* * Locals: ");
				Arrays.asList(currentLocals).forEach(dn -> System.out.println("* * * " + dn));
				System.out.println("* * Stack: ");
				currentStack.forEach(dn -> System.out.println("* * * " + dn));
			}

			assertSameTypeOnFrameLocals(methodControlFlow.frameAt(currentIndex));
			assertSameSizeOnFrameStack(methodControlFlow.frameAt(currentIndex));

			toReturn = processInstruction(HEAP, toReturn, invokations, putFieldNodes, putStaticNodes);

			int nextIndex = methodCodeIterator.lookAhead();
			if (nextIndex < firstPos + length) {
				final Frame nextFrame = methodControlFlow.frameAt(nextIndex);

				if (TRACE) {
					System.out.println("* After: ");
					System.out.println("* " + nextFrame);
					System.out.println("* * Locals: ");
					Arrays.asList(currentLocals).forEach(dn -> System.out.println("* * * " + dn));
					System.out.println("* * Stack: ");
					currentStack.forEach(dn -> System.out.println("* * * " + dn));
					System.out.println();
				}

				assertSameTypeOnFrameLocals(nextFrame);
				assertSameSizeOnFrameStack(nextFrame);
			}
		}

		return new BlockDataGraph(setOfAllNodes.toArray(DataNode[]::new), incLocalNodes, incStack,
				invokations.toArray(Invocation[]::new), DataNode.EMPTY_DATA_NODES, DataNode.EMPTY_DATA_NODES,
				currentLocals, toReturn == null ? DataNode.EMPTY_DATA_NODES : new DataNode[] { toReturn }, currentStack,
				putFieldNodes.toArray(PutFieldNode[]::new), putStaticNodes.toArray(PutStaticNode[]::new));
	}

	/**
	 * Returns the line number of the source line corresponding to the current
	 * bytecode position contained in current method.
	 * 
	 * @return -1 if this information is not available.
	 * @see MethodInfo#getLineNumber(int)i
	 */
	int getCurrentLineNumber() {
		return this.methodInfo.getLineNumber(this.currentIndex);
	}

	int getCurrentOp() {
		return methodCodeIterator.byteAt(currentIndex);
	}

	@SneakyThrows
	private Type getType(int tagIndex) {
		int tag = methodConstPool.getTag(tagIndex);
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

	@SneakyThrows
	private Type getTypeOfNextStackTop() {
		final Frame nextInstructionFrame = methodControlFlow.frameAt(methodCodeIterator.lookAhead());
		final int topIndex = nextInstructionFrame.getTopIndex();
		final Type onTopOfStack = nextInstructionFrame.getStack(topIndex);
		if (onTopOfStack == Type.TOP) {
			final Type beforeTop = nextInstructionFrame.getStack(topIndex - 1);
			assert beforeTop.getSize() == 2;
			return beforeTop;
		}
		return onTopOfStack;
	}

	@SneakyThrows
	private DataNode processInstruction(final Heap heap, DataNode toReturn, final List<Invocation> invokations,
			final List<PutFieldNode> putFieldNodes, final List<PutStaticNode> putStaticNodes) {

		int op = getCurrentOp();
		switch (op) {
		case Opcode.ACONST_NULL:
			currentStack.push(dataNodeFactory.newNullConst());
			break;

		case Opcode.ALOAD:
			currentStack.push(currentLocals[methodCodeIterator.byteAt(currentIndex + 1)]);
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
			processInstructionWithStackOnly(1);
			break;

		case Opcode.ASTORE:
			currentLocals[methodCodeIterator.byteAt(currentIndex + 1)] = currentStack.pop();
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
			processInstructionWithStackOnly(2);
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
			processInstructionWithStackOnly(0);
			break;

		case Opcode.CHECKCAST:
			processInstructionWithStackOnly(1);
			break;

		case Opcode.DLOAD: {
			final int varIndex = methodCodeIterator.byteAt(currentIndex + 1);
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
			final int varIndex = methodCodeIterator.byteAt(currentIndex + 1);
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
			currentStack.push(currentLocals[methodCodeIterator.byteAt(currentIndex + 1)]);
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
			int constantIndex = methodCodeIterator.u16bitAt(currentIndex + 1);
			final String fieldrefClassName = methodConstPool.getFieldrefClassName(constantIndex);
			final String fieldrefName = methodConstPool.getFieldrefName(constantIndex);
			final String fieldrefType = methodConstPool.getFieldrefType(constantIndex);

			final CtClass fieldClass = classPool.get(fieldrefClassName);
			final CtField ctField = fieldClass.getField(fieldrefName, fieldrefType);

			// XXX: USE HEAP
			if (op == Opcode.GETFIELD) {
				processInstructionWithStackOnly(1, () -> dataNodeFactory.newGetField(fieldClass, ctField));
			} else {
				processInstructionWithStackOnly(0, () -> dataNodeFactory.newGetStatic(fieldClass, ctField));
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
			processInstructionWithStackOnly(1);
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
			processInstructionWithStackOnly(2);
			break;

		case Opcode.ICONST_0:
		case Opcode.ICONST_1:
		case Opcode.ICONST_2:
		case Opcode.ICONST_3:
		case Opcode.ICONST_4:
		case Opcode.ICONST_5:
			currentStack.push(dataNodeFactory.newIntConst(op - Opcode.ICONST_0));
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
			DataNode prevValue = currentLocals[methodCodeIterator.byteAt(currentIndex + 1)];
			DataNode nextValue = dataNodeFactory.newDataNode().setInputs(new DataNode[] { prevValue }).setOperation(op)
					.setType(prevValue.getType());
			currentLocals[methodCodeIterator.byteAt(currentIndex + 1)] = nextValue;
			break;
		}

		case Opcode.ILOAD:
			currentStack.push(currentLocals[methodCodeIterator.byteAt(currentIndex + 1)]);
			break;
		case Opcode.ILOAD_0:
		case Opcode.ILOAD_1:
		case Opcode.ILOAD_2:
		case Opcode.ILOAD_3:
			currentStack.push(currentLocals[op - Opcode.ILOAD_0]);
			break;

		case Opcode.INEG:
		case Opcode.INSTANCEOF: {
			processInstructionWithStackOnly(1);
			break;
		}

		case Opcode.INVOKEDYNAMIC: {
			int constantIndex = methodCodeIterator.u16bitAt(currentIndex + 1);

			final int nameAndType = methodConstPool.getInvokeDynamicNameAndType(constantIndex);
			final String signature = methodConstPool.getUtf8Info(methodConstPool.getNameAndTypeDescriptor(nameAndType));

			final MethodSignature methodSignature = SignatureAttribute.toMethodSignature(signature);
			processInstructionWithStackOnly(methodSignature.getParameterTypes().length);
			break;
		}

		case Opcode.INVOKEINTERFACE:
		case Opcode.INVOKESPECIAL:
		case Opcode.INVOKESTATIC:
		case Opcode.INVOKEVIRTUAL: {
			int constantIndex = methodCodeIterator.u16bitAt(currentIndex + 1);

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
				className = methodConstPool.getMethodrefClassName(constantIndex);
				methodName = methodConstPool.getMethodrefName(constantIndex);
				signature = methodConstPool.getMethodrefType(constantIndex);
			} else {
				final int nameAndType = methodConstPool.getInvokeDynamicNameAndType(constantIndex);
				methodName = methodConstPool.getUtf8Info(methodConstPool.getNameAndTypeName(nameAndType));
				signature = methodConstPool.getUtf8Info(methodConstPool.getNameAndTypeDescriptor(nameAndType));
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
				result = dataNodeFactory.newDataNode();
				result.inputs = inputsArray;
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
			currentLocals[methodCodeIterator.byteAt(currentIndex + 1)] = currentStack.pop();
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
			processInstructionWithStackOnly(2);
			break;

		case Opcode.LCONST_0:
		case Opcode.LCONST_1:
			currentStack.push(dataNodeFactory.newLongConst(op - Opcode.LCONST_0));
			break;

		case Opcode.LDC:
		case Opcode.LDC_W: {
			int tagIndex = op == Opcode.LDC ? methodCodeIterator.byteAt(currentIndex + 1)
					: methodCodeIterator.u16bitAt(currentIndex + 1);
			String description = "constant #" + tagIndex;

			Type type = getType(tagIndex);
			if (type.getCtClass().getName().equals("java.lang.String")) {
				description = "\"" + methodConstPool.getStringInfo(tagIndex) + "\"";
			}

			currentStack.push(dataNodeFactory.newDataNode().setDescription(description).setType(type));
			break;
		}

		case Opcode.LDC2_W: {
			int tagIndex = methodCodeIterator.u16bitAt(currentIndex + 1);
			String description = "constant #" + tagIndex;

			Type type = getType(tagIndex);
			if (type.getCtClass().getName().equals("java.lang.String")) {
				description = "\"" + methodConstPool.getUtf8Info(currentIndex) + "\"";
			}

			currentStack.push(dataNodeFactory.newDataNode().setDescription(description).setType(type));
			break;
		}

		case Opcode.LLOAD: {
			final int varIndex = methodCodeIterator.byteAt(currentIndex + 1);
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
			final int varIndex = methodCodeIterator.byteAt(currentIndex + 1);
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
			processInstructionWithStackOnly(0);
			break;
		case Opcode.NEWARRAY:
			processInstructionWithStackOnly(1);
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
			int constantIndex = methodCodeIterator.u16bitAt(currentIndex + 1);
			final String fieldrefClassName = methodConstPool.getFieldrefClassName(constantIndex);
			final String fieldrefName = methodConstPool.getFieldrefName(constantIndex);
			final String fieldrefType = methodConstPool.getFieldrefType(constantIndex);

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
			int constantValue = methodCodeIterator.u16bitAt(currentIndex + 1);
			currentStack.push(
					dataNodeFactory.newDataNode().setDescription("sipush " + constantValue).setType(Type.INTEGER));
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

	private void processInstructionWithStackOnly(final int toPoll) {
		processInstructionWithStackOnly(toPoll, dataNodeFactory::newDataNode);
	}

	private void processInstructionWithStackOnly(final int toPoll, Supplier<DataNode> resultTypeSupplier) {
		DataNode result = resultTypeSupplier.get().setType(getTypeOfNextStackTop());
		DataNode[] inputs = new DataNode[toPoll];
		for (int i = 0; i < toPoll; i++) {
			inputs[i] = currentStack.pop();
		}
		result.inputs = inputs;
		currentStack.push(result);
	}

}
