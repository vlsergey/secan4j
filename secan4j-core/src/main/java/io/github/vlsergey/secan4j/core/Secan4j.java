package io.github.vlsergey.secan4j.core;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Predicate;

import io.github.vlsergey.secan4j.core.session.IntesectionsCollector;
import io.github.vlsergey.secan4j.core.session.PaintingSession;
import javassist.ClassPool;
import javassist.CtMethod;
import javassist.LoaderClassPath;
import picocli.CommandLine;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

public class Secan4j {

	public static final class ClassPathStringConverter implements ITypeConverter<URL[]> {

		@Override
		public URL[] convert(String value) throws Exception {
			final String[] classPathElements = value.split(System.getProperty("path.separator"));
			final URL[] classPathUrls = new URL[classPathElements.length];
			for (int i = 0; i < classPathElements.length; i++) {
				classPathUrls[i] = new File(classPathElements[i]).toURI().toURL();
			}
			return classPathUrls;
		}

	}

	public static void main(String[] args) {
		System.exit(new CommandLine(new Callable<Integer>() {

			@Option(names = { "--basePackage" }, description = "Base package to scan for entry points")
			private String basePackage;

			@Parameters(index = "0", arity = "1", description = "Application classpath to scan (URLs)", converter = ClassPathStringConverter.class)
			private List<URL[]> classPath;

			@Override
			public Integer call() throws Exception {
				final URL[] wholeClassPath = classPath.stream().flatMap(cp -> Arrays.stream(cp)).toArray(URL[]::new);
				final URLClassLoader classLoader = new URLClassLoader(wholeClassPath);

				final ClassPool classPool = new ClassPool(true);
				classPool.insertClassPath(new LoaderClassPath(classLoader));

				final IntesectionsCollector intesectionsCollector = new IntesectionsCollector();
				final PaintingSession paintingSession = new PaintingSession(classPool, intesectionsCollector);

				final Predicate<CtMethod> methodPredicate = new MethodHasAnnotationPredicate(
						new AnnotatedByGraph(classPool), "org.springframework.web.bind.annotation.Mapping");

				final List<CtMethod> entryPoints = new ClassPathScannerFacade<CtMethod>(basePackage, wholeClassPath,
						classPool, ctClass -> Arrays.stream(ctClass.getMethods()).filter(methodPredicate)).scan()
								.collect(toList());
				for (CtMethod ctMethod : entryPoints) {
					paintingSession.analyze(ctMethod);
				}

				intesectionsCollector.getTraces().values().forEach(traceList -> {
					System.err.println(
							"Found source-sink link with following trace:\n" + traceList.stream().map(traceItem -> {
								StringBuilder builder = new StringBuilder("* ");
								builder.append(Optional
										.ofNullable(traceItem.getSourceCodePosition()).map(scp -> scp.getClassName()
												+ ":" + scp.getMethodName() + ":" + scp.getSourceLine())
										.orElse("(no source code position)"));
								builder.append(" ");
								builder.append(traceItem.toString());
								return builder.toString();
							}).collect(joining("\n")));
				});

				return intesectionsCollector.getTraces().size();
			}

		}).execute(args));
	}

}
