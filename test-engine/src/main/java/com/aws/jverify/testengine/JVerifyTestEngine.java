package com.aws.jverify.testengine;

import com.aws.jverify.common.AnnotatedRange;
import com.aws.jverify.common.Position;
import com.aws.jverify.common.Range;
import com.aws.jverify.verifier.DafnyDiagnostic;
import com.aws.jverify.verifier.Driver;
import com.aws.jverify.verifier.SourceFile;
import com.aws.jverify.verifier.VerifierOptions;
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
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.InflaterOutputStream;

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

            // We assume that the working directory is the root of the current Gradle project (as is default),
            // and that the test sources are in the standard "src/test/java" subdirectory of the project root.
            var sourceRoot = Path.of(System.getProperty("user.dir") , "src", "test", "java");
            if (!Files.exists(sourceRoot) || !Files.isDirectory(sourceRoot)) {
                Assertions.fail(() ->
                        "Test sources root directory for %s not found at %s".formatted(testClass, sourceRoot));
            }

            // We assume the class's source file is at the path corresponding to its package and class name,
            // just as Java requires for public classes.
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

    /**
     * @see #testMarkedSource(SourceFile, JVerifyTest)
     */
    public static void testMarkedSource(Path markedSourcePath, JVerifyTest annotation) throws IOException {
        var markedSource = Files.readString(markedSourcePath);
        testMarkedSource(new SourceFile(markedSourcePath, markedSource), annotation);
    }

    /**
     * Verifies the given source code and asserts that the exit code, emitted diagnostics,
     * and verified/error counts (from Dafny) match the specified values in the source code's test metadata.
     */
    public static void testMarkedSource(SourceFile markedSourceFile, JVerifyTest annotation) throws IOException {
        var parsedMarkup = TestMarkup.getPositionsAndAnnotatedRanges(markedSourceFile.getCharContent(false));
        List<AnnotatedRange> ranges = parsedMarkup.ranges();
        verifyFile(markedSourceFile, annotation, ranges);
    }

    public static void verifyFile(SourceFile markedSourceFile, JVerifyTest annotation, List<AnnotatedRange> ranges) throws IOException {
        Assumptions.assumeTrue(annotation.skip() == null || annotation.skip().isEmpty(), annotation.skip());

        assertThat("@VerifyTest must include both or neither of dafnyVerified and dafnyErrors",
                (annotation.dafnyVerified() >= 0) == (annotation.dafnyErrors() >= 0));

        var options = getVerifierOptions(annotation);
        var inputs = Arrays.stream(annotation.additionalFiles()).map(f -> {
            try {
                var p = Path.of(markedSourceFile.toUri()).getParent().resolve(f);
                String markedSource = Files.readString(p);
                return (JavaFileObject)new SourceFile(p, markedSource);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }).collect(Collectors.toList());
        inputs.add(markedSourceFile);
        var verificationResults = Driver.verifyJavaFiles(inputs, options);

        var diagnosticsAsAnnotations = verificationResults.getDiagnostics().stream()
                .flatMap(diagnostic -> diagnostic instanceof DafnyDiagnostic dafnyDiagnostic
                        ? dafnyDiagnostic.flattenRelated()
                        : Stream.of(diagnostic))
                .map(JVerifyTestEngine::diagnosticAsAnnotatedRange)
                .sorted()
                .toList();
        var expectedAnnotations = ranges.stream().sorted().toList();
        assertThat("diagnostics", diagnosticsAsAnnotations, equalTo(expectedAnnotations));

        Integer expectedDafnyVerifiedCount = annotation.dafnyVerified() >= 0 ? annotation.dafnyVerified() : null;
        Integer expectedDafnyErrorCount = annotation.dafnyErrors() >= 0 ? annotation.dafnyErrors() : null;
        Assertions.assertAll(
                () -> assertThat("exit code",
                        verificationResults.getExitCode(),
                        is(annotation.exitCode())),
                () -> assertThat("Dafny verified count",
                        verificationResults.getDafnyVerifiedCount(),
                        is(expectedDafnyVerifiedCount)),
                () -> assertThat("Dafny error count",
                        verificationResults.getDafnyErrorCount(),
                        is(expectedDafnyErrorCount))
        );
    }

    private static AnnotatedRange diagnosticAsAnnotatedRange(Diagnostic<?> diagnostic) {
        var startPos = new Position(diagnostic.getLineNumber(), diagnostic.getColumnNumber());
        var endPos = diagnostic instanceof DafnyDiagnostic dafnyDiagnostic
                ? new Position(dafnyDiagnostic.getEndLineNumber(), dafnyDiagnostic.getEndColumnNumber())
                : new Position(startPos.line(), startPos.character() + 1);
        var range = new Range(startPos, endPos);
        return new AnnotatedRange(Driver.formatMessage(diagnostic), range);
    }

    private static final boolean IS_WINDOWS = System.getProperty("os.name", "").toLowerCase().contains("windows");

    private static VerifierOptions getVerifierOptions(JVerifyTest annotation) {
        var dafnyPath = Path.of("../dafny").toAbsolutePath()
                .resolve(IS_WINDOWS ? "Binaries/Dafny.exe" : "Scripts/dafny");
        var libraryJar = Path.of("../library/build/libs/library-1.0-SNAPSHOT.jar");
        var testEngineClassPath = Path.of("../test-engine/build/classes/java/main").toAbsolutePath();
        var prelude = Path.of("../verifier/src/main/resources/additional.dfy");
        return new VerifierOptions(
                dafnyPath,
                List.of(libraryJar, testEngineClassPath),
                prelude,
                Path.of("../build/temp.dfy"),
                Path.of("../build/temp.dbin"),
                true,
                true,
                new String[] {
                        "--use-basename-for-filename",
                       //"--wait-for-debugger",
                },
                annotation.verifyByDefault()
        );
    }

    /**
     * For creating a JVerifyTest annotation without having it in source code.
     * Useful for testing things like examples where we don't want the explicit annotation.
     */
    public static JVerifyTest makeJVerifyTestAnnotation(boolean verifyByDefault, int exitCode, int dafnyVerified, int dafnyErrors) {
        return new JVerifyTest() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return JVerifyTest.class;
            }

            @Override
            public String skip() {
                return "";
            }

            @Override
            public boolean verifyByDefault() {
                return verifyByDefault;
            }

            @Override
            public int exitCode() {
                return exitCode;
            }

            @Override
            public int dafnyVerified() {
                return dafnyVerified;
            }

            @Override
            public int dafnyErrors() {
                return dafnyErrors;
            }

            @Override
            public String[] additionalFiles() {
                return new String[0];
            }
        };
    }
}
