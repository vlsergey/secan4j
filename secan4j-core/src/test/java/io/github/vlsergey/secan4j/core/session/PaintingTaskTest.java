package io.github.vlsergey.secan4j.core.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Test;

import io.github.vlsergey.secan4j.core.colored.ColoredObject;
import io.github.vlsergey.secan4j.core.colored.Confidence;
import io.github.vlsergey.secan4j.core.colored.SimpleColoredMethods;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.NotFoundException;
import javassist.bytecode.analysis.Type;
import lombok.NonNull;

class PaintingTaskTest extends BasePaintingSessionTest {

	@Test
	void testThatDemultiplexingWillNotCreateNewItemsIndefinitly() throws NotFoundException {
		final CtClass ctClass = classPool.get(SimpleColoredMethods.class.getName());
		final CtMethod ctMethod = ctClass.getDeclaredMethod("append");

		ColoredObject colored1 = ColoredObject.sourceOnRoot(TestTraceItem.INSTANCE, Type.OBJECT.getCtClass(),
				Confidence.EXPLICITLY);
		ColoredObject colored2 = ColoredObject.sourceOnRoot(TestTraceItem.INSTANCE,
				classPool.get(String.class.getName()), Confidence.EXPLICITLY);
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
