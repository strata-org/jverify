package com.aws.jverify.testengine;

import com.aws.jverify.common.AnnotatedRange;
import com.aws.jverify.common.Position;
import com.aws.jverify.common.Range;
import com.aws.jverify.verifier.*;
import com.google.auto.service.AutoService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.platform.commons.support.ReflectionSupport;
import org.junit.platform.engine.DiscoverySelector;
import org.junit.platform.engine.EngineDiscoveryRequest;
import org.junit.platform.engine.ExecutionRequest;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestEngine;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.discovery.ClassSelector;
import org.junit.platform.engine.discovery.ClasspathRootSelector;
import org.junit.platform.engine.discovery.MethodSelector;
import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor;
import org.junit.platform.engine.support.descriptor.EngineDescriptor;
import org.junit.platform.engine.support.hierarchical.EngineExecutionContext;
import org.junit.platform.engine.support.hierarchical.HierarchicalTestEngine;
import org.junit.platform.engine.support.hierarchical.Node;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.*;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@AutoService(TestEngine.class)
public class JVerifyTestEngine extends HierarchicalTestEngine<EngineExecutionContext> {
    static final Logger LOGGER = Logger.getLogger(JVerifyTestEngine.class.getName());

    @Override
    public String getId() {
        return "jverify-test-engine";
    }

    @Override
    public TestDescriptor discover(EngineDiscoveryRequest request, UniqueId uniqueId) {
        var descriptor = new EngineDescriptor(uniqueId, "JVerify Tests");
        request.getSelectorsByType(DiscoverySelector.class).stream()
                .flatMap(selector -> switch (selector) {
                    case ClassSelector classSelector ->
                            Stream.of(classSelector.getJavaClass()).filter(this::isJVerifyTest);
                    case ClasspathRootSelector cpRootSelector ->
                            ReflectionSupport.findAllClassesInClasspathRoot(
                                    cpRootSelector.getClasspathRoot(), this::isJVerifyTest, _ -> true)
                            .stream();
                    case MethodSelector methodSelector -> {
                        var testClass = methodSelector.getJavaClass();
                        LOGGER.warning(() ->
                                "Verifying individual methods isn't supported yet; verifying its class %s instead"
                                        .formatted(testClass.getName()));
                        yield Stream.of(testClass).filter(this::isJVerifyTest);
                    }
                    default -> {
                        LOGGER.warning(() -> "Unexpected selector: " + selector);
                        throw new UnsupportedOperationException("Unsupported selector: " + selector);
                    }
                })
                .forEach(testClass -> {
                    LOGGER.fine(() -> "Registering test for class: " + testClass);
                    var id = uniqueId.append("class", testClass.getName());
                    descriptor.addChild(new JVerifyTestDescriptor(id, testClass));
                });
        return descriptor;
    }

    private boolean isJVerifyTest(Class<?> clazz) {
        return clazz.isAnnotationPresent(JVerifyTest.class);
    }

    static class JVerifyTestDescriptor
            extends AbstractTestDescriptor
            implements Node<EngineExecutionContext>
    {
        private final Class<?> testClass;

        JVerifyTestDescriptor(UniqueId uniqueId, Class<?> testClass) {
            super(uniqueId, "Verify %s".formatted(testClass.getSimpleName()));
            this.testClass = testClass;
        }

        @Override
        public Type getType() {
            return Type.TEST;
        }

        @Override
        public EngineExecutionContext execute(EngineExecutionContext context, DynamicTestExecutor dynamicTestExecutor)
                throws IOException
        {
            LOGGER.fine(() -> "Executing test for class: " + testClass);

            var sourceRoot = Path.of(System.getProperty("user.dir") , "src", "test", "java");
            if (!Files.exists(sourceRoot) || !Files.isDirectory(sourceRoot)) {
                Assertions.fail(() ->
                        "Test sources root directory for %s not found at %s".formatted(testClass, sourceRoot));
            }

            var pkgPath = sourceRoot.resolve("", testClass.getPackageName().split("\\."));
            var sourcePath = pkgPath.resolve(testClass.getSimpleName() + ".java");
            if (!Files.exists(sourcePath) || !Files.isRegularFile(sourcePath)) {
                Assertions.fail(() ->
                        "Source file for %s not found at %s".formatted(testClass, sourcePath));
            }
            LOGGER.fine(() -> "Found source file: " + sourcePath);

            testMarkedSource(sourcePath, testClass.getAnnotation(JVerifyTest.class));
            return context;
        }
    }

    @Override
    protected EngineExecutionContext createExecutionContext(ExecutionRequest request) {
        return null;
    }

    public static void testMarkedSource(Path markedSourcePath, JVerifyTest annotation) throws IOException {
        var markedSource = Files.readString(markedSourcePath);
        testMarkedSource(new SourceFile(markedSourcePath, markedSource), annotation);
    }

    public static void testMarkedSource(SourceFile markedSourceFile, JVerifyTest annotation) throws IOException {
        var parsedMarkup = TestMarkup.getPositionsAndAnnotatedRanges(markedSourceFile.getCharContent(false));
        List<AnnotatedRange> ranges = parsedMarkup.ranges();
        verifyFile(markedSourceFile, annotation, ranges);
    }

    public static void verifyFile(SourceFile sourceFile, JVerifyTest annotation, List<AnnotatedRange> ranges) throws IOException {
        verifyFile(sourceFile, annotation, ranges, getVerifierOptions(annotation, null));
    }

    public static void verifyFile(SourceFile sourceFile, JVerifyTest annotation, List<AnnotatedRange> ranges,
                                  VerifierOptions options) throws IOException {
        Assumptions.assumeTrue(annotation.skip() == null || annotation.skip().isEmpty(), annotation.skip());

        var inputs = Arrays.stream(annotation.additionalFiles()).map(f -> {
            try {
                var p = Path.of(sourceFile.toUri()).getParent().resolve(f).normalize();
                String markedSource = Files.readString(p);
                return (JavaFileObject)new SourceFile(p, markedSource);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }).collect(Collectors.toList());
        inputs.add(sourceFile);
        var results = Driver.getDriver(options).verifyJavaFiles(inputs);

        var diagnosticsAsAnnotations = results.diagnostics().stream()
                .map(d -> diagnosticAsAnnotatedRange(sourceFile.toUri(), d))
                .sorted()
                .toList();

        if (Boolean.parseBoolean(System.getenv("JVERIFY_UPDATE_TEST_ANNOTATIONS"))) {
            if (results.exitCode() == 0 || results.exitCode() == 4) {
                updateTestAnnotation(sourceFile, annotation, results);
            }
        }

        var expectedAnnotations = ranges.stream().sorted().toList();
        assertThat("diagnostics", diagnosticsAsAnnotations, equalTo(expectedAnnotations));

        Integer expectedJavaVerifiedCount = annotation.methodsVerified() >= 0 ? annotation.methodsVerified() : null;
        Integer expectedMethodsInvalidCount = annotation.methodsInvalid() >= 0 ? annotation.methodsInvalid() : null;
        Integer expectedErrorCount = annotation.methodsInvalid() >= 0 ? annotation.errorCount() : null;
        Integer expectedJavaSkippedCount = annotation.methodsSkipped() >= 0 ? annotation.methodsSkipped() : null;
        Assertions.assertAll(
            () -> assertThat("exit code",
                    results.exitCode(),
                    is(annotation.exitCode())),
            () -> {
                if (expectedJavaVerifiedCount != null) {
                    assertThat("Java verified methods count",
                            results.verificationResults().verificationPassedMethods(),
                        is(expectedJavaVerifiedCount));
                }
            },
            () -> {
                if (expectedMethodsInvalidCount != null) {
                    assertThat("Java failed methods count",
                            results.verificationResults().verificationFailedMethods(),
                            is(expectedMethodsInvalidCount));
                }
            },
            () -> {
                if (expectedErrorCount != null) {
                    assertThat("Failed error count",
                            results.verificationResults().verificationFailedAssertions(),
                            is(expectedErrorCount));
                }
            },
            () -> {
                if (expectedJavaSkippedCount != null) {
                    assertThat("Java verification skipped method count",
                            results.verificationResults().verificationSkippedMethods(),
                            is(expectedJavaSkippedCount));
                }
            }
        );
    }

    private static AnnotatedRange diagnosticAsAnnotatedRange(URI testFile, Diagnostic<?> diagnostic) {
        var source = diagnostic.getSource();
        URI sourceUri = null;
        if (source instanceof URI uri) {
            sourceUri = uri;
        } else if (source instanceof SourceFile javaFileObject) {
            sourceUri = javaFileObject.toUri();
        }
        var startPos = new Position(diagnostic.getLineNumber(), diagnostic.getColumnNumber());
        var endPos = diagnostic instanceof DiagnosticWithRange diagnosticWithRange
                ? new Position(diagnosticWithRange.getRange().end().line(), diagnosticWithRange.getRange().end().character())
                : new Position(startPos.line(), startPos.character() + 1);
        var range = new Range(startPos, endPos);
        if (sourceUri == null || sourceUri.equals(testFile.normalize())) {
            return new AnnotatedRange(Driver.formatMessage(diagnostic), range);
        } else {
            Range zeroRange = new Range(new Position(1, 1), new Position(1, 2));
            String path;
            if (sourceUri.getScheme().equals("string")) {
                path = sourceUri.getPath();
            } else {
                path = testFile.resolve(".").relativize(sourceUri).getPath();
            }
            return new AnnotatedRange(path + "(" + range + ") " + Driver.formatMessage(diagnostic), zeroRange);
        }
    }

    public static VerifierOptions getVerifierOptions(JVerifyTest annotation, PositionFilter positionFilter) {
        var backendPath = Path.of("../Strata").toAbsolutePath().normalize();
        var libraryJar = Path.of("../library/build/libs/library-1.0-SNAPSHOT.jar");
        var verifierJar = Path.of("../verifier/build/libs/verifier-1.0-SNAPSHOT.jar");
        var libraryForTestingClassPath = Path.of("../library-for-testing/build/libs/library-for-testing-1.0-SNAPSHOT.jar");
        var testEngineClassPath = Path.of("../test-engine/build/classes/java/main").toAbsolutePath();
        var workingDirectory = Path.of(System.getProperty("user.dir"));
        return new VerifierOptions(new PrintWriter(System.out),
                workingDirectory,
                backendPath,
                List.of(verifierJar, libraryJar, testEngineClassPath, libraryForTestingClassPath),
                Path.of("../build/temp.dbin"),
                true,
                List.of(),
                true,
                annotation.verifyByDefault(),
                annotation.continueOnErrors(),
                positionFilter, true, false
        );
    }

    public static JVerifyTest makeJVerifyTestAnnotation(int methodsVerified, int assertionsFailed) {
        return makeJVerifyTestAnnotation(true, assertionsFailed > 0 ? 4 : 0, methodsVerified, assertionsFailed,
                false, true);
    }

    public static JVerifyTest makeJVerifyTestAnnotation(boolean verifyByDefault, int exitCode,
                                                        int methodsVerified, int assertionsFailed,
                                                        boolean continueOnErrors,
                                                        boolean useBuiltinContracts) {
        return new JVerifyTestRecord("", verifyByDefault, useBuiltinContracts, continueOnErrors,
                exitCode, new String[0], -1, assertionsFailed, methodsVerified, -1,
                new int[] {});
    }

    public static void updateTestAnnotation(SourceFile sourceFile, JVerifyTest annotation, JVerifyResults results) throws IOException {
        try (BufferedReader reader = new BufferedReader(sourceFile.openReader(false))) {
            var allLines = reader.lines().toArray(String[]::new);
            var maybeAnnotationIndex = IntStream.range(0, allLines.length)
                    .filter(index -> allLines[index].startsWith("@JVerifyTest"))
                    .findFirst();
            if (maybeAnnotationIndex.isPresent()) {
                int annotationIndex = maybeAnnotationIndex.getAsInt();
                var newLine = allLines[annotationIndex].
                        replaceFirst("exitCode = \\d", "exitCode = " + results.exitCode()).
                        replaceFirst("javaVerified = \\d+", "javaVerified = " + results.verificationResults().verificationPassedMethods()).
                        replaceFirst("javaErrors = \\d+", "javaErrors = " + results.verificationResults().verificationFailedMethods());
                allLines[annotationIndex] = newLine;
            }

            try (BufferedWriter writer = new BufferedWriter(new FileWriter(sourceFile.toUri().getPath()))) {
                for (String line : allLines) {
                    writer.write(line);
                    writer.newLine();
                }
                writer.flush();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
