package io.github.vlsergey.secan4j.core.colorless;

import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Namespace;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.bytecode.Mnemonic;
import javassist.bytecode.Opcode;
import lombok.NonNull;
import lombok.SneakyThrows;
import picocli.CommandLine;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

public class ColorlessGraphExported {
	private static final String DATA_KEY_COLOR = "color";

	private static final String DATA_KEY_LABEL = "d4";
	private static final String DATA_KEY_STYLE = "d7";
	private static final XMLEventFactory eventFactory = XMLEventFactory.newInstance();
	private static final Namespace NS_GRAPHML = eventFactory.createNamespace("g",
			"http://graphml.graphdrawing.org/xmlns");

	private static final Namespace NS_X = eventFactory.createNamespace("x",
			"http://www.yworks.com/xml/yfiles-common/markup/3.0");
	private static final Namespace NS_Y = eventFactory.createNamespace("y",
			"http://www.yworks.com/xml/yfiles-common/3.0");
	private static final Namespace NS_YJS = eventFactory.createNamespace("yjs",
			"http://www.yworks.com/xml/yfiles-for-html/2.0/xaml");
	private static final QName QNAME_DATA = new QName(NS_GRAPHML.getNamespaceURI(), "data", NS_GRAPHML.getPrefix());
	private static final QName QNAME_EDGE = new QName(NS_GRAPHML.getNamespaceURI(), "edge", NS_GRAPHML.getPrefix());
	private static final QName QNAME_GRAPH = new QName(NS_GRAPHML.getNamespaceURI(), "graph", NS_GRAPHML.getPrefix());

	private static final QName QNAME_GRAPHML = new QName(NS_GRAPHML.getNamespaceURI(), "graphml",
			NS_GRAPHML.getPrefix());

	private static final QName QNAME_KEY = new QName(NS_GRAPHML.getNamespaceURI(), "key", NS_GRAPHML.getPrefix());
	private static final QName QNAME_NODE = new QName(NS_GRAPHML.getNamespaceURI(), "node", NS_GRAPHML.getPrefix());

	private static final QName QNAME_X_LIST = new QName(NS_X.getNamespaceURI(), "List", NS_X.getPrefix());

	private static final QName QNAME_Y_LABEL = new QName(NS_Y.getNamespaceURI(), "Label", NS_Y.getPrefix());
	private static final QName QNAME_Y_LABEL_TEXT = new QName(NS_Y.getNamespaceURI(), "Label.Text", NS_Y.getPrefix());
	private static final QName QNAME_YJS_SHAPE_NODE_STYLE = new QName(NS_YJS.getNamespaceURI(), "ShapeNodeStyle",
			NS_YJS.getPrefix());

	@SneakyThrows
	private static void addLabel(final String labelText, final @NonNull XMLEventWriter writer) {
		writer.add(eventFactory.createStartElement(QNAME_DATA,
				Arrays.asList(eventFactory.createAttribute("key", DATA_KEY_LABEL)).iterator(), null));
		writer.add(eventFactory.createStartElement(QNAME_X_LIST, null, null));
		writer.add(eventFactory.createStartElement(QNAME_Y_LABEL, null, null));
		writer.add(eventFactory.createStartElement(QNAME_Y_LABEL_TEXT, null, null));
		writer.add(eventFactory.createCData(labelText));
		writer.add(eventFactory.createEndElement(QNAME_Y_LABEL_TEXT, null));
		writer.add(eventFactory.createEndElement(QNAME_Y_LABEL, null));
		writer.add(eventFactory.createEndElement(QNAME_X_LIST, null));
		writer.add(eventFactory.createEndElement(QNAME_DATA, null));
	}

	@SneakyThrows
	private static void addStyle(final String color, final @NonNull XMLEventWriter writer) {
		writer.add(eventFactory.createStartElement(QNAME_DATA,
				Arrays.asList(eventFactory.createAttribute("key", DATA_KEY_STYLE)).iterator(), null));
		writer.add(eventFactory.createStartElement(QNAME_YJS_SHAPE_NODE_STYLE, Arrays
				.asList(eventFactory.createAttribute("stroke", color), eventFactory.createAttribute("fill", color))
				.iterator(), null));
		writer.add(eventFactory.createEndElement(QNAME_YJS_SHAPE_NODE_STYLE, null));
		writer.add(eventFactory.createEndElement(QNAME_DATA, null));
	}

	public static void main(String[] args) {
		int exitCode = new CommandLine(new Callable<Integer>() {

			@Parameters(index = "0", description = "Fully qualified class name to dump")
			private String className;

			@Option(names = { "-m", "--method" }, description = "Method name")
			private String method;

			@Override
			public Integer call() throws Exception {
				final ClassPool classPool = ClassPool.getDefault();
				final CtClass ctClass = classPool.get(className);

				for (CtMethod ctMethod : ctClass.getMethods()) {
					if (method != null && !ctMethod.getName().equals(method)) {
						continue;
					}

					final Optional<BlockDataGraph> opGraph = new ColorlessGraphBuilder().buildGraph(ctClass, ctMethod);
					if (opGraph.isEmpty() || (opGraph.get().getMethodReturnNodes().length == 0
							&& opGraph.get().getInvokations().length == 0))
						continue;

					System.out.println();
					System.out.println("    =====8<=====    ");
					System.out.println();
					System.out.println(ctClass.getName());
					System.out.println(ctMethod.getLongName());
					System.out.println();
					final ColorlessGraphExported exported = new ColorlessGraphExported();
					exported.export(opGraph.get(), XMLOutputFactory.newInstance().createXMLEventWriter(System.out));
					System.out.println();
					System.out.println("    =====8<=====    ");
					System.out.println();
				}
				return 0;
			}
		}).execute(args);
		System.exit(exitCode);
	}

	@SneakyThrows
	private String dumpWithIncomings(final @NonNull Map<DataNode, String> doneAlready, final @NonNull DataNode node,
			final @NonNull XMLEventWriter writer) {
		if (doneAlready.containsKey(node)) {
			return doneAlready.get(node);
		}

		String id = "node" + doneAlready.size();
		doneAlready.put(node, id);

		writer.add(eventFactory.createStartElement(QNAME_NODE,
				Arrays.asList(eventFactory.createAttribute("id", id)).iterator(), null));

		StringWriter label = new StringWriter();
		label.append(node.getDescription());
		label.append("\n");
		if (node.getOperation() != Opcode.NOP) {
			label.append(Mnemonic.OPCODE[node.getOperation()]);
			label.append("\n");
		}
		if (node.getType() != null) {
			label.append(node.getType().toString());
			label.append("\n");
		}
		addLabel(label.toString(), writer);

		writer.add(eventFactory.createEndElement(QNAME_NODE, null));

		for (DataNode incNode : node.getInputs()) {
			String incNodeId = dumpWithIncomings(doneAlready, incNode, writer);

			writeEdge(incNodeId, id, writer);
		}

		return id;
	}

	@SneakyThrows
	public void export(BlockDataGraph graph, XMLEventWriter writer) {
		writer.add(eventFactory.createStartDocument(StandardCharsets.UTF_8.name()));
		writer.add(eventFactory.createStartElement(QNAME_GRAPHML, null,
				Arrays.asList(NS_GRAPHML, NS_X, NS_Y, NS_YJS).iterator()));

		writer.add(eventFactory.createStartElement(QNAME_KEY, Arrays.asList( //
				eventFactory.createAttribute("id", DATA_KEY_COLOR), //
				eventFactory.createAttribute("for", "node"), //
				eventFactory.createAttribute("attr.name", "color"), //
				eventFactory.createAttribute("attr.type", "string")).iterator(), null));
		writer.add(eventFactory.createEndElement(QNAME_KEY, null));

		writer.add(eventFactory.createStartElement(QNAME_KEY, Arrays.asList( //
				eventFactory.createAttribute("id", DATA_KEY_LABEL), //
				eventFactory.createAttribute("for", "node"), //
				eventFactory.createAttribute("attr.name", "NodeLabels"), //
				eventFactory.createAttribute(NS_Y.getPrefix(), NS_Y.getNamespaceURI(), "attr.uri",
						"http://www.yworks.com/xml/yfiles-common/2.0/NodeLabels"))
				.iterator(), null));
		writer.add(eventFactory.createEndElement(QNAME_KEY, null));

		writer.add(eventFactory.createStartElement(QNAME_KEY, Arrays.asList( //
				eventFactory.createAttribute("id", DATA_KEY_STYLE), //
				eventFactory.createAttribute("for", "node"), //
				eventFactory.createAttribute("attr.name", "NodeStyle"), //
				eventFactory.createAttribute(NS_Y.getPrefix(), NS_Y.getNamespaceURI(), "attr.uri",
						"http://www.yworks.com/xml/yfiles-common/2.0/NodeStyle"))
				.iterator(), null));
		writer.add(eventFactory.createEndElement(QNAME_KEY, null));

		writer.add(eventFactory.createStartElement(QNAME_GRAPH,
				Arrays.asList(eventFactory.createAttribute("edgedefault", "directed")).iterator(), null));

		final HashMap<DataNode, String> nodeToId = new HashMap<>();
		for (DataNode outs : graph.getMethodReturnNodes()) {
			dumpWithIncomings(nodeToId, outs, writer);
		}

		writeInvokations(graph, writer, nodeToId);

		writer.add(eventFactory.createEndElement(QNAME_GRAPH, null));
		writer.add(eventFactory.createEndElement(QNAME_GRAPHML, null));
		writer.add(eventFactory.createEndDocument());
		writer.flush();
	}

	private void writeEdge(String sorce, String target, final XMLEventWriter writer) throws XMLStreamException {
		writer.add(eventFactory.createStartElement(QNAME_EDGE, Arrays
				.asList(eventFactory.createAttribute("source", sorce), eventFactory.createAttribute("target", target))
				.iterator(), null));
		writer.add(eventFactory.createEndElement(QNAME_EDGE, null));
	}

	private void writeInvokations(BlockDataGraph graph, XMLEventWriter writer, final HashMap<DataNode, String> nodeToId)
			throws XMLStreamException {
		int counter = 0;
		for (Invocation invokation : graph.getInvokations()) {
			final String id = "invokation" + (counter++);

			writer.add(eventFactory.createStartElement(QNAME_NODE,
					Arrays.asList(eventFactory.createAttribute("id", id)).iterator(), null));
			addStyle("#00FFFF", writer);

			addLabel(invokation.getClassName() + "\n" + invokation.getMethodName(), writer);
			writer.add(eventFactory.createEndElement(QNAME_NODE, null));

			for (DataNode param : invokation.getParameters()) {
				dumpWithIncomings(nodeToId, param, writer);
				writeEdge(nodeToId.get(param), id, writer);
			}

			final DataNode resultNode = invokation.getResult();
			if (resultNode != null) {
				dumpWithIncomings(nodeToId, resultNode, writer);
				writeEdge(id, nodeToId.get(resultNode), writer);
			}
		}
	}
}
