package com.aws.jverify.testengine;

import com.aws.jverify.common.AnnotatedRange;
import com.aws.jverify.common.Position;
import com.aws.jverify.common.Range;
import com.aws.jverify.common.TestMarkup;
import com.aws.jverify.verifier.DafnyDiagnostic;
import com.aws.jverify.verifier.Driver;
import com.aws.jverify.verifier.SourceFile;
import com.aws.jverify.verifier.VerifierOptions;
import com.google.auto.service.AutoService;
import org.checkerframework.checker.nullness.qual.Nullable;
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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.greaterThan;
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

            testMarkedSource(sourcePath);
            return context;
        }
    }

    @Override
    protected EngineExecutionContext createExecutionContext(ExecutionRequest request) {
        return null;
    }

    /**
     * @see #testMarkedSource(SourceFile)
     */
    public static void testMarkedSource(Path markedSourcePath) throws IOException {
        var markedSource = Files.readString(markedSourcePath);
        testMarkedSource(new SourceFile(markedSourcePath, markedSource));
    }

    /**
     * Verifies the given source code and asserts that the exit code, emitted diagnostics,
     * and verified/error counts (from Dafny) match the specified values in the source code's test metadata.
     * See {@link #parseMetadata(String)} for details on the metadata format.
     */
    public static void testMarkedSource(SourceFile markedSourceFile) throws IOException {
        var parsedMarkup = TestMarkup.getPositionsAndAnnotatedRanges(markedSourceFile.getCharContent(false));
        var source = parsedMarkup.output();
        var metadata = parseMetadata(source);
        Assumptions.assumeFalse(metadata == null, "Skipping test according to metadata");

        var options = getVerifierOptions();
        var verificationResults = Driver.verifyJavaFile(markedSourceFile, options);

        var diagnosticsAsAnnotations = verificationResults.getDiagnostics().stream()
                .flatMap(diagnostic -> diagnostic instanceof DafnyDiagnostic dafnyDiagnostic
                        ? dafnyDiagnostic.flattenRelated()
                        : Stream.of(diagnostic))
                .map(JVerifyTestEngine::diagnosticAsAnnotatedRange)
                .sorted()
                .toList();
        var expectedAnnotations = parsedMarkup.ranges().stream().sorted().toList();
        assertThat("diagnostics", diagnosticsAsAnnotations, equalTo(expectedAnnotations));

        assertThat("exit code", verificationResults.getExitCode(), is(metadata.exitCode));
        assertThat("Dafny verified count", verificationResults.getDafnyVerifiedCount(), is(metadata.dafnyVerified));
        assertThat("Dafny error count", verificationResults.getDafnyErrorCount(), is(metadata.dafnyErrors));
//        assertThat("Dafny error count", verificationResults.getDafnyErrorCount(),
//                is(Objects.requireNonNullElse(metadata.dafnyErrors, 0)));
//        if (metadata.dafnyVerified != null) {
//            assertThat("Dafny verified count", verificationResults.getDafnyVerifiedCount(), is(metadata.dafnyVerified));
//        } else {
//            // Sanity check that we don't have 0 verified, which is usually a sign we didn't verify at all
//            assertThat("Dafny verified count", verificationResults.getDafnyVerifiedCount(), greaterThan(0));
//        }
    }

    private static final Pattern TEST_METADATA_PATTERN = Pattern.compile("^// TEST: (.+)$", Pattern.MULTILINE);

    private record TestMetadata(int exitCode, Integer dafnyVerified, Integer dafnyErrors) {}

    /**
     * Parses and returns test metadata from the given source content,
     * or returns {@code null} if the metadata indicates that the test should be skipped.
     * Throws if the test metadata is absent or malformed.
     * <p>
     * Valid metadata formats:
     * {@snippet :
     * (1)
     * // TEST: skip
     *
     * (2)
     * // TEST: exitCode=X
     *
     * (3)
     * // TEST: exitCode=X dafnyVerified=Y dafnyErrors=Z
     * }
     * <ol>
     *     <li>The test should be skipped.</li>
     *     <li>
     *         Verification should finish with exit code {@code X} without Dafny terminating normally
     *         (i.e. Dafny is never invoked because there are javac errors, or Dafny terminates abnormally).
     *     </li>
     *     <li>
     *         Verification should finish with exit code {@code X}, Dafny terminates normally,
     *         and Dafny's summary reports {@code Y} verified symbols and {@code Z} errors.
     *     </li>
     * </ol>
     */
    private static @Nullable TestMetadata parseMetadata(String source) {
        var metadataMatcher = TEST_METADATA_PATTERN.matcher(source);
        if (!metadataMatcher.find()) {
            throw new AssertionError("Test metadata not found");
        }
        var tokens = Arrays.asList(metadataMatcher.group(1).split("\\s+"));
        if (tokens.contains("skip")) {
            assertThat("'skip' must not appear with other tokens", tokens.size(), is(1));
            return null;
        }

        Integer exitCode = null;
        Integer dafnyVerified = null;
        Integer dafnyErrors = null;
        for (var token : tokens) {
            var parts = token.split("=", 2);
            assertThat("Metadata token must have key=value format", parts.length, is(2));
            switch (parts[0]) {
                case "exitCode" -> exitCode = Integer.parseInt(parts[1]);
                case "dafnyVerified" -> dafnyVerified = Integer.parseInt(parts[1]);
                case "dafnyErrors" -> dafnyErrors = Integer.parseInt(parts[1]);
                default -> Assertions.fail("Invalid token in test metadata: " + token);
            }
        }
        assertThat("Metadata must include expectedExitCode", exitCode, notNullValue());
        assertThat("Metadata must include both or neither of dafnyVerified and dafnyErrors",
                (dafnyVerified == null) == (dafnyErrors == null));

        return new TestMetadata(exitCode, dafnyVerified, dafnyErrors);
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

    private static VerifierOptions getVerifierOptions() {
        var dafnyPath = Path.of("../dafny").toAbsolutePath()
                .resolve(IS_WINDOWS ? "Binaries/Dafny.exe" : "Scripts/dafny");
        var libraryJar = Path.of("../library/build/libs/library.jar");
        var testEngineClassPath = Path.of("../test-engine/build/classes/java/main").toAbsolutePath();
        var prelude = Path.of("../verifier/src/main/resources/additional.dfy");
        return new VerifierOptions(
                dafnyPath,
                libraryJar,
                List.of(testEngineClassPath),
                prelude,
                Path.of("../temp.dfy"),
                null,
                true,
                new String[] {
                        "--use-basename-for-filename",
//                        "--wait-for-debugger",
                }
        );
    }
}
