package io.github.vlsergey.secan4j.core.colorless;

import static java.util.Collections.unmodifiableCollection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.IntFunction;

import javassist.ClassPool;
import javassist.CtBehavior;
import javassist.CtClass;
import javassist.Modifier;
import javassist.NotFoundException;
import javassist.bytecode.CodeAttribute;
import javassist.bytecode.CodeIterator;
import javassist.bytecode.ConstPool;
import javassist.bytecode.analysis.ControlFlow;
import javassist.bytecode.analysis.ControlFlow.Block;
import javassist.bytecode.analysis.ControlFlow.Node;
import javassist.bytecode.analysis.Frame;
import javassist.bytecode.analysis.Type;
import lombok.Data;
import lombok.NonNull;
import lombok.SneakyThrows;

public class ColorlessMethodGraphBuilder {

	@Data
	private static class BlockDataGraphKey {
		final Block block;
		final DataNode[] incLocalNodes;
		final Collection<DataNode> incStackNodes;
	}

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

	private final @NonNull ClassPool classPool;
	private final @NonNull CtClass ctClass;
	private final @NonNull CtBehavior ctMethod;
	private final @NonNull Block[] methodBasicBlocks;
	private final @NonNull CodeAttribute methodCodeAttribute;
	private final @NonNull CodeIterator methodCodeIterator;
	private final @NonNull ConstPool methodConstPool;
	private final @NonNull ControlFlow methodControlFlow;

	@SneakyThrows
	public ColorlessMethodGraphBuilder(final @NonNull ClassPool classPool, final @NonNull CtClass ctClass,
			final @NonNull CtBehavior ctMethod) {
		this.classPool = classPool;
		this.ctClass = ctClass;
		this.ctMethod = ctMethod;

		this.methodControlFlow = new ControlFlow(ctClass, ctMethod.getMethodInfo());
		this.methodBasicBlocks = methodControlFlow.basicBlocks();

		this.methodCodeAttribute = ctMethod.getMethodInfo2().getCodeAttribute();
		if (methodCodeAttribute != null) {
			this.methodConstPool = methodCodeAttribute.getConstPool();
			this.methodCodeIterator = methodCodeAttribute.iterator();
		} else {
			this.methodConstPool = null;
			this.methodCodeIterator = null;
		}
	}

	@SneakyThrows
	public @NonNull Optional<BlockDataGraph> buildGraph() {
		if (this.methodBasicBlocks == null || this.methodBasicBlocks.length == 0) {
			return Optional.empty();
		}

		final Node rootNode = methodControlFlow.dominatorTree()[0];
		final Block rootBlock = rootNode.block();
		final Frame rootFrame = methodControlFlow.frameAt(rootBlock.position());

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
		final int[] blockEnters = new int[methodBasicBlocks.length];
		buildGraphRecursively(done, rootNode.block(), incLocalNodes, EMPTY_STACK, blockEnters);

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
	private void buildGraphRecursively(final @NonNull Map<BlockDataGraphKey, BlockDataGraph> done,
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

		BlockDataGraph blockGraph = new ColorlessBlockGraphBuilder(classPool, methodCodeIterator, methodConstPool,
				methodControlFlow, block, incLocalNodes, incStackNodes).buildGraph();
		done.put(key, blockGraph);

		for (int e = 0; e < block.exits(); e++) {
			Block exitBlock = block.exit(e);
			buildGraphRecursively(done, exitBlock, blockGraph.getOutLocalNodes(), blockGraph.getOutStackNodes(),
					blockEnters);
		}

	}

}
