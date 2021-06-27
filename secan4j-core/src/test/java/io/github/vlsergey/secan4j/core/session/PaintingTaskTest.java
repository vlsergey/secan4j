package io.github.vlsergey.secan4j.core.session;

import static java.util.Collections.singletonMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Test;

import io.github.vlsergey.secan4j.core.colored.ColoredObject;
import io.github.vlsergey.secan4j.core.colored.Confidence;
import io.github.vlsergey.secan4j.core.colored.SimpleColoredMethods;
import io.github.vlsergey.secan4j.core.colored.TraceItem;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.NotFoundException;
import javassist.bytecode.analysis.Type;
import lombok.NonNull;

class PaintingTaskTest {

	private final static class TestTraceItem implements TraceItem {
		@Override
		public Map<String, ?> describe() {
			return singletonMap("description", "TestTraceItem");
		}

		@Override
		public TraceItem findPrevious() {
			return null;
		}
	}

	private final ClassPool classPool = ClassPool.getDefault();

	@Test
	void testThatDemultiplexingWillNotCreateNewItemsIndefinitly() throws NotFoundException {
		final CtClass ctClass = classPool.get(SimpleColoredMethods.class.getName());
		final CtMethod ctMethod = ctClass.getDeclaredMethod("append");

		ColoredObject colored1 = ColoredObject.sourceOnRoot(new TestTraceItem(), Type.OBJECT.getCtClass(),
				Confidence.EXPLICITLY);
		ColoredObject colored2 = ColoredObject.sourceOnRoot(new TestTraceItem(), classPool.get(String.class.getName()),
				Confidence.EXPLICITLY);
		ColoredObject merged = ColoredObject.merge(colored1, colored2, (a, b) -> {
			fail("no merge problems were expected");
		});
		assertEquals(2, merged.getSeenClassesHere().size());

		final @NonNull Map<PaintingTask.TaskKey, PaintingTask> allNodes = new ConcurrentHashMap<>();
		for (int i = 0; i < 3; i++) {
			ColoredObject.demultiplex(new ColoredObject[] { merged }, (singleClassIns) -> {
				allNodes.computeIfAbsent(new PaintingTask.TaskKey(ctMethod, singleClassIns, new ColoredObject[0]),
						PaintingTask::new);
			});
		}

		assertEquals(2, allNodes.size());
	}

}
