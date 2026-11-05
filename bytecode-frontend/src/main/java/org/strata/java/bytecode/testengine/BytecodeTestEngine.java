package org.strata.java.bytecode.testengine;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;
import org.strata.jverify.testengine.JVerifyTestEngine;
import org.strata.jverify.testengine.JVerifyTestRecord;
import org.strata.jverify.testengine.TestMarkup;

import org.strata.jverify.common.AnnotatedRange;
import org.strata.jverify.common.Position;
import org.strata.jverify.common.Range;
import org.strata.jverify.verifier.DiagnosticWithRange;
import org.strata.jverify.verifier.JVerifyResults;
import org.strata.jverify.verifier.SourceFile;
import org.strata.jverify.verifier.StrataTimeout;
import org.strata.jverify.verifier.VerificationResults;
import org.strata.java.bytecode.verifier.VerifierDriver;
import org.strata.java.bytecode.verifier.VerifierOptions;
import com.google.auto.service.AutoService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.checkerframework.checker.nullness.qual.Nullable;
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
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.EngineDescriptor;
import org.junit.platform.engine.support.hierarchical.EngineExecutionContext;
import org.junit.platform.engine.support.hierarchical.HierarchicalTestEngine;
import org.junit.platform.engine.support.hierarchical.Node;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * JUnit Platform engine that runs {@code @JVerifyTest} verification tests
 * through the bytecode frontend ({@code VerifierDriver}: javac -g -Xjcov ->
 * classfiles -> Laurel -> Strata), matching each test's diagnostics against
 * its inline markup and its expected exit code and counts.
 */
@AutoService(TestEngine.class)
public class BytecodeTestEngine extends HierarchicalTestEngine<EngineExecutionContext> {
    static final Logger LOGGER = Logger.getLogger(BytecodeTestEngine.class.getName());

    /**
     * Anchor range for a diagnostic reported against a file other than the one
     * under test. Such a diagnostic carries its real range inside its annotation
     * text ({@code path(range) message}), so the range itself is fixed — it only
     * has to be stable and identical on both the expected and actual side.
     */
    private static final Range CROSS_FILE_RANGE = new Range(new Position(1, 1), new Position(1, 2));

    @Override
    public String getId() {
        return "bytecode-test-engine";
    }

    @Override
    public TestDescriptor discover(EngineDiscoveryRequest request, UniqueId uniqueId) {
        var descriptor = new EngineDescriptor(uniqueId, "JVerify Tests (bytecode frontend)");
        // Self-gating: Brazil flattens every module's output onto a shared
        // runtime classpath, so this auto-registered engine is visible to
        // other modules' test tasks too. It stays inert unless activated by
        // `bytecode.corpus.root` (set only by the tasks that run @JVerifyTest
        // fixtures: :bytecode-frontend:corpusTest and :bytecode-frontend:examplesTest) -- discovering nothing
        // here means JUnit never executes it.
        if (System.getProperty("bytecode.corpus.root") == null) {
            return descriptor;
        }
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
                    default -> Stream.<Class<?>>empty();
                })
                .forEach(testClass -> {
                    LOGGER.fine(() -> "Registering test for class: " + testClass);
                    var id = uniqueId.append("class", testClass.getName());
                    var container = new JVerifyClassDescriptor(id, testClass);
                    container.addChild(new JVerifyTestDescriptor(id.append("test", "verify"), testClass));
                    descriptor.addChild(container);
                });
        return descriptor;
    }

    private boolean isJVerifyTest(Class<?> clazz) {
        return clazz.isAnnotationPresent(JVerifyTest.class);
    }

    static class JVerifyClassDescriptor
            extends AbstractTestDescriptor
            implements Node<EngineExecutionContext> {
        JVerifyClassDescriptor(UniqueId uniqueId, Class<?> testClass) {
            super(uniqueId, testClass.getSimpleName(), ClassSource.from(testClass));
        }

        @Override
        public Type getType() {
            return Type.CONTAINER;
        }
    }

    static class JVerifyTestDescriptor
            extends AbstractTestDescriptor
            implements Node<EngineExecutionContext> {
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
                throws IOException {
            LOGGER.fine(() -> "Executing test for class: " + testClass);

            // The fixtures' source root is supplied by the build via the
            // bytecode.corpus.root property (falls back to this project's own
            // src/test/java).
            var sourceRoot = corpusSourceRoot();
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

            testMarkedSource(sourcePath, testClass.getAnnotation(JVerifyTest.class), testClass.getName());
            return context;
        }
    }

    @Override
    protected EngineExecutionContext createExecutionContext(ExecutionRequest request) {
        return null;
    }

    /**
     * Root of the fixtures' sources. Supplied by the build via the
     * {@code bytecode.corpus.root} system property (the verification corpus
     * is {@code verifier/src/test/java}); falls back to this project's own
     * {@code src/test/java}.
     */
    static Path corpusSourceRoot() {
        var prop = System.getProperty("bytecode.corpus.root");
        if (prop != null && !prop.isBlank()) {
            // Normalized so fixture URIs match the driver's diagnostic URIs
            // (a ".." segment would make every diagnostic look cross-file).
            return Path.of(prop).toAbsolutePath().normalize();
        }
        return Path.of(System.getProperty("user.dir"), "src", "test", "java");
    }

    /**
     * @see #testMarkedSource(SourceFile, JVerifyTest)
     */
    public static void testMarkedSource(Path markedSourcePath, JVerifyTest annotation) throws IOException {
        testMarkedSource(markedSourcePath, annotation, null);
    }

    static void testMarkedSource(Path markedSourcePath, JVerifyTest annotation, @Nullable String className)
            throws IOException {
        var markedSource = Files.readString(markedSourcePath);
        testMarkedSource(new SourceFile(markedSourcePath, markedSource), annotation, className);
    }

    /**
     * Verifies the given source code and asserts that the exit code, emitted
     * diagnostics, and verified/error counts match the values in the source
     * code's test metadata.
     */
    public static void testMarkedSource(SourceFile markedSourceFile, JVerifyTest annotation) throws IOException {
        testMarkedSource(markedSourceFile, annotation, null);
    }

    static void testMarkedSource(SourceFile markedSourceFile, JVerifyTest annotation, @Nullable String className)
            throws IOException {
        var parsedMarkup = TestMarkup.getPositionsAndAnnotatedRanges(markedSourceFile.getCharContent(false));
        List<AnnotatedRange> ranges = new ArrayList<>(parsedMarkup.ranges());
        ranges.addAll(additionalFileRanges(markedSourceFile, annotation));
        ranges.addAll(JVerifyTestEngine.contractFileRanges(markedSourceFile, annotation));
        var options = getVerifierOptions(annotation, JVerifyTestEngine.contractSources(markedSourceFile, annotation));
        if (className != null && Boolean.getBoolean(CortexXfailManifest.PROPERTY)) {
            verifyFileOnCortex(markedSourceFile, annotation, ranges, options, className);
        } else {
            verifyFile(markedSourceFile, annotation, ranges, options);
        }
    }

    private static final class CortexXfails {
        static final CortexXfailManifest INSTANCE = CortexXfailManifest.load();
    }

    private static void verifyFileOnCortex(SourceFile sourceFile, JVerifyTest annotation,
                                           List<AnnotatedRange> ranges, VerifierOptions options,
                                           String className) throws IOException {
        var skip = CortexXfails.INSTANCE.skipReason(className);
        Assumptions.assumeTrue(skip == null, "SKIP on Cortex: " + skip);
        var xfail = CortexXfails.INSTANCE.forcedXfail(className);
        if (xfail != null) {
            Assumptions.assumeTrue(annotation.skip() == null || annotation.skip().isEmpty(), annotation.skip());
            applyXfailInversion("Cortex " + xfail.blocker() + ": " + xfail.reason(),
                    "Remove its entry from cortex-xfails.txt.",
                    () -> runAndCheck(sourceFile, annotation, ranges, options));
            return;
        }
        if (CortexXfails.INSTANCE.isForcedRun(className)) {
            Assumptions.assumeTrue(annotation.skip() == null || annotation.skip().isEmpty(), annotation.skip());
            runAndCheck(sourceFile, annotation, ranges, options);
            return;
        }
        verifyFile(sourceFile, annotation, ranges, options);
    }

    /**
     * Expected diagnostics declared by markup inside {@code additionalFiles},
     * rewritten into the {@code path(range) message} cross-file form.
     */
    private static List<AnnotatedRange> additionalFileRanges(SourceFile markedSourceFile, JVerifyTest annotation)
            throws IOException {
        var testFile = markedSourceFile.toUri();
        var parent = Path.of(testFile).getParent();
        List<AnnotatedRange> ranges = new ArrayList<>();
        for (String additionalFile : annotation.additionalFiles()) {
            var path = parent.resolve(additionalFile).normalize();
            var uri = path.toUri();
            var parsed = TestMarkup.getPositionsAndAnnotatedRanges(Files.readString(path));
            if (parsed.ranges().isEmpty()) {
                continue;
            }
            var relative = testFile.resolve(".").relativize(uri).getPath();
            for (AnnotatedRange range : parsed.ranges()) {
                ranges.add(new AnnotatedRange(
                        relative + "(" + range.range() + ") " + range.annotation(),
                        CROSS_FILE_RANGE));
            }
        }
        return ranges;
    }

    public static void verifyFile(SourceFile sourceFile, JVerifyTest annotation, List<AnnotatedRange> ranges)
            throws IOException {
        verifyFile(sourceFile, annotation, ranges,
                getVerifierOptions(annotation, JVerifyTestEngine.contractSources(sourceFile, annotation)));
    }

    public static void verifyFile(SourceFile sourceFile, JVerifyTest annotation, List<AnnotatedRange> ranges,
                                  VerifierOptions options) throws IOException {
        // skip= and expectedToFail are mutually exclusive: skip is evaluated
        // first, so a test with both would abort as skipped and never reach
        // the XPASS tripwire.
        if (annotation.expectedToFail() && annotation.skip() != null && !annotation.skip().isEmpty()) {
            throw new IllegalStateException("@JVerifyTest sets both skip and expectedToFail; "
                    + "these are mutually exclusive -- clear skip so the xfail/XPASS check runs.");
        }
        // Legacy hard skip: still supported, but discouraged. Prefer expectedToFail.
        Assumptions.assumeTrue(annotation.skip() == null || annotation.skip().isEmpty(), annotation.skip());

        if (!annotation.expectedToFail()) {
            runAndCheck(sourceFile, annotation, ranges, options);
            return;
        }
        // xfail: run it, but invert the outcome.
        applyXfailInversion(describeBlockers(annotation),
                "Remove expectedToFail/blockedBy and adopt it as a normal test.",
                () -> runAndCheck(sourceFile, annotation, ranges, options));
    }

    /** A check that may throw the checked exceptions {@link #runAndCheck} does. */
    @FunctionalInterface
    public interface VerificationCheck {
        void run() throws IOException;
    }

    /**
     * Run an expected-to-fail {@code check} and invert its outcome. Only
     * AssertionError is an "expected failure"; a genuine infra error is NOT
     * caught here and propagates, so an xfail test that stops *running* fails
     * loudly instead of masquerading as still-broken. Completing with no
     * failure means the blocker(s) cleared -> fail loudly (XPASS) to demand the
     * {@code expectedToFail} be removed.
     */
    public static void applyXfailInversion(String why, String remedy, VerificationCheck check) throws IOException {
        try {
            check.run();
        } catch (AssertionError expected) {
            Assumptions.assumeTrue(false, "Expected failure (" + why + "): " + expected.getMessage());
            return;
        }
        Assertions.fail("XPASS: this test is marked expectedToFail (" + why + ") but now PASSES. " + remedy);
    }

    private static String describeBlockers(JVerifyTest annotation) {
        return annotation.blockedBy().length == 0 ? "unspecified blocker"
                : Arrays.toString(annotation.blockedBy());
    }

    private static void runAndCheck(SourceFile sourceFile, JVerifyTest annotation, List<AnnotatedRange> ranges,
                                    VerifierOptions options) throws IOException {
        var inputs = Arrays.stream(annotation.additionalFiles()).map(f -> {
            try {
                var p = Path.of(sourceFile.toUri()).getParent().resolve(f).normalize();
                String markedSource = Files.readString(p);
                return (JavaFileObject) new SourceFile(p, markedSource);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }).collect(Collectors.toList());
        inputs.add(sourceFile);
        var results = new VerifierDriver(options).verifyJavaFiles(inputs);
        checkResults(results, sourceFile, annotation, ranges);
    }

    /**
     * A NoVerdict run reached a test with verification-count expectations —
     * a front-end usage error. An AssertionError subtype so an
     * {@code expectedToFail} test swallows it while a normal test fails loud.
     */
    public static final class NoVerdictExpectation extends AssertionError {
        NoVerdictExpectation(String message) {
            super(message);
        }
    }

    public static final class StrataRejection extends AssertionError {
        StrataRejection(String message) {
            super(message);
        }
    }

    public static final List<String> STRATA_PROGRAM_REJECTIONS = List.of(
            "Laurel resolution failed",
            "Laurel to Core translation failed",
            "Core type checking failed");

    public static Optional<StrataRejection> strataRejection(JVerifyResults results, JVerifyTest annotation) {
        if (!annotation.interpret() || !(results instanceof JVerifyResults.DidNotRun didNotRun)) {
            return Optional.empty();
        }
        var messages = results.diagnostics().stream().map(d -> d.getMessage(null)).filter(m -> m != null).toList();
        if (messages.stream().anyMatch(m -> m.toUpperCase(Locale.ROOT).contains("PANIC") || m.contains(StrataTimeout.MESSAGE_PREFIX))) {
            return Optional.empty();
        }
        int strataExitCode = didNotRun.strataExitCode();
        boolean rejected = switch (strataExitCode) {
            case 2, 4 -> true;
            case 1 -> messages.stream().anyMatch(m -> STRATA_PROGRAM_REJECTIONS.stream().anyMatch(m::contains));
            default -> false;
        };
        if (!rejected) {
            return Optional.empty();
        }
        return Optional.of(new StrataRejection("Strata rejected the program (Strata exit " + strataExitCode + "): "
                + String.join("; ", messages)));
    }

    /**
     * Assert a verification {@code results} against the test's expectations.
     */
    public static void checkResults(JVerifyResults results, SourceFile sourceFile,
                                    JVerifyTest annotation, List<AnnotatedRange> ranges) {
        // Not a verification outcome: a Strata program rejection is an
        // AssertionError an xfail test may absorb; any other abort fails LOUD.
        if (results instanceof JVerifyResults.DidNotRun) {
            strataRejection(results, annotation).ifPresent(e -> { throw e; });
            var timeoutReason = results.diagnostics().stream().map(d -> d.getMessage(null))
                    .filter(m -> m != null && m.contains(StrataTimeout.MESSAGE_PREFIX))
                    .findFirst().map(m -> m.substring(m.indexOf(StrataTimeout.MESSAGE_PREFIX)) + "; ").orElse("");
            throw new IllegalStateException("Strata backend did not run (exit "
                    + results.exitCode() + "): " + timeoutReason
                    + "infrastructure failure, not a verification result.");
        }

        var diagnosticsAsAnnotations = results.diagnostics().stream()
                .map(d -> diagnosticAsAnnotatedRange(sourceFile.toUri(), d))
                .map(BytecodeTestEngine::toLineLevel)
                .sorted()
                .toList();

        VerificationResults vr = results.verdictOrNull();
        Integer expectedJavaVerifiedCount = annotation.methodsVerified() >= 0 ? annotation.methodsVerified() : null;
        Integer expectedMethodsInvalidCount = annotation.methodsInvalid() >= 0 ? annotation.methodsInvalid() : null;
        Integer expectedErrorCount = annotation.methodsInvalid() >= 0 ? annotation.errorCount() : null;
        Integer expectedJavaSkippedCount = annotation.methodsSkipped() >= 0 ? annotation.methodsSkipped() : null;
        if (vr == null && (expectedJavaVerifiedCount != null || expectedMethodsInvalidCount != null
                || expectedErrorCount != null || expectedJavaSkippedCount != null)) {
            throw new NoVerdictExpectation("test set verification-count expectations but the run "
                    + "produced no verdict (exit " + results.exitCode() + "): verification did not run to a verdict");
        }

        var expectedAnnotations = ranges.stream()
                .map(BytecodeTestEngine::toLineLevel)
                .sorted()
                .toList();
        assertThat("diagnostics", diagnosticsAsAnnotations, equalTo(expectedAnnotations));

        Assertions.assertAll(
                () -> assertThat("exit code",
                        results.exitCode(),
                        is(annotation.exitCode())),
                () -> {
                    if (expectedJavaVerifiedCount != null) {
                        assertThat("Java verified methods count",
                                vr.verificationPassedMethods(),
                                is(expectedJavaVerifiedCount));
                    }
                },
                () -> {
                    if (expectedMethodsInvalidCount != null) {
                        assertThat("Java failed methods count",
                                vr.verificationFailedMethods(),
                                is(expectedMethodsInvalidCount));
                    }
                },
                () -> {
                    if (expectedErrorCount != null) {
                        assertThat("Failed error count",
                                vr.verificationFailedAssertions(),
                                is(expectedErrorCount));
                    }
                },
                () -> {
                    if (expectedJavaSkippedCount != null) {
                        assertThat("Java verification skipped method count",
                                vr.verificationSkippedMethods(),
                                is(expectedJavaSkippedCount));
                    }
                }
        );
    }

    /** A {@code L:C-L:C} range embedded in a cross-file annotation's text. */
    private static final java.util.regex.Pattern EMBEDDED_RANGE =
            java.util.regex.Pattern.compile("(\\d+):\\d+-\\d+:\\d+");

    /**
     * Collapse a diagnostic (or expected markup) range to LINE precision. The
     * bytecode frontend derives positions from the CharacterRangeTable /
     * LineNumberTable, so only a diagnostic's start LINE is reliable: the CRT
     * span includes the trailing {@code ';'}, sub-expression anchors fall back
     * to the enclosing statement, and class-level (@Contract) diagnostics have
     * no bytecode position at all. Verdicts, counts, messages, and the start
     * line stay exact; only column and end precision is relaxed. Applied identically to the expected and
     * actual sides -- including the range embedded in a cross-file annotation's
     * {@code path(range) message} text -- so a wrong line still fails.
     */
    private static AnnotatedRange toLineLevel(AnnotatedRange ar) {
        int line = ar.range().start().line();
        var range = new Range(new Position(line, 1), new Position(line, 1));
        String annotation = EMBEDDED_RANGE.matcher(ar.annotation()).replaceAll(m -> m.group(1));
        return new AnnotatedRange(annotation, range);
    }

    private static AnnotatedRange diagnosticAsAnnotatedRange(URI testFile, Diagnostic<?> diagnostic) {
        var source = diagnostic.getSource();
        URI sourceUri = null;
        if (source instanceof URI uri) {
            sourceUri = uri;
        } else if (source instanceof SourceFile javaFileObject) {
            sourceUri = javaFileObject.toUri();
        } else if (diagnostic instanceof DiagnosticWithRange withRange) {
            // Diagnostics parsed from Strata's output carry no JavaFileObject;
            // their file is on the diagnostic itself.
            sourceUri = withRange.getUri();
        }
        var startPos = new Position(diagnostic.getLineNumber(), diagnostic.getColumnNumber());
        var endPos = diagnostic instanceof DiagnosticWithRange diagnosticWithRange
                ? new Position(diagnosticWithRange.getRange().end().line(),
                        diagnosticWithRange.getRange().end().character())
                : new Position(startPos.line(), startPos.character() + 1);
        var range = new Range(startPos, endPos);
        if (sourceUri == null || sourceUri.equals(testFile.normalize())) {
            return new AnnotatedRange(VerifierDriver.formatMessage(diagnostic), range);
        } else {
            String path;
            if (sourceUri.getScheme().equals("string")) {
                path = sourceUri.getPath();
            } else {
                path = testFile.resolve(".").relativize(sourceUri).getPath();
            }
            return new AnnotatedRange(path + "(" + range + ") " + VerifierDriver.formatMessage(diagnostic),
                    CROSS_FILE_RANGE);
        }
    }

    public static VerifierOptions getVerifierOptions(JVerifyTest annotation) {
        if (annotation.contractSourcePath().length > 0) {
            throw new IllegalArgumentException("contractSourcePath is relative to the test file;"
                    + " pass the resolved JVerifyTestEngine.contractSources(...)");
        }
        return getVerifierOptions(annotation, annotation.useBuiltinContracts()
                ? List.of(JVerifyTestEngine.getBuiltinContractsSourceDir())
                : List.of());
    }

    /** Verifier options for {@code annotation}, with {@code contractSources} as the contractSourcePath. */
    public static VerifierOptions getVerifierOptions(JVerifyTest annotation, List<Path> contractSources) {
        // Classpath for the -g -Xjcov compile of the test sources: the spec
        // library, the test engine (@JVerifyTest) and library-for-testing,
        // all from the loaded classes' code sources.
        var libraryClassPath = VerifierDriver.specLibraryLocation();
        var testEngineClassPath = codeSourceOf(JVerifyTest.class);
        var libraryForTestingClassPath = codeSourceOf(org.strata.jverify.testing.LibraryClassA.class);
        var workingDirectory = Path.of(System.getProperty("user.dir"));
        return new VerifierOptions(new PrintWriter(System.out),
                workingDirectory,
                List.of(libraryClassPath, testEngineClassPath, libraryForTestingClassPath),
                null,
                false,
                true,
                contractSources,
                true,
                annotation.verifyByDefault(),
                annotation.continueOnErrors(),
                true,
                false,
                annotation.interpret(),
                annotation.entry().isEmpty() ? null : annotation.entry(),
                annotation.fuel() > 0 ? annotation.fuel() : null,
                false,  // test cases are compiled by the driver's own javac: always verifiable
                annotation.interpreter().isEmpty() ? null : annotation.interpreter(),
                null,
                annotation.interpret()
        ).withAllowUncheckedExceptions(annotation.allowUncheckedExceptions());
    }

    private static Path codeSourceOf(Class<?> clazz) {
        return Path.of(URI.create(clazz.getProtectionDomain().getCodeSource().getLocation().toString()));
    }

    /**
     * For creating a JVerifyTest annotation without having it in source code
     * (e.g. driving examples without an explicit annotation).
     */
    public static JVerifyTest makeJVerifyTestAnnotation(int methodsVerified, int assertionsFailed) {
        return makeJVerifyTestAnnotation(true, assertionsFailed > 0 ? 4 : 0, methodsVerified, assertionsFailed,
                false, false);
    }

    public static JVerifyTest makeJVerifyTestAnnotation(boolean verifyByDefault, int exitCode,
                                                        int methodsVerified, int assertionsFailed,
                                                        boolean continueOnErrors,
                                                        boolean useBuiltinContracts) {
        return makeJVerifyTestAnnotation(verifyByDefault, exitCode, methodsVerified, assertionsFailed,
                continueOnErrors, useBuiltinContracts, false, new Blocker[0]);
    }

    /** As above, but lets a test set the xfail fields (expectedToFail / blockedBy). */
    public static JVerifyTest makeJVerifyTestAnnotation(boolean verifyByDefault, int exitCode,
                                                        int methodsVerified, int assertionsFailed,
                                                        boolean continueOnErrors,
                                                        boolean useBuiltinContracts,
                                                        boolean expectedToFail, Blocker[] blockedBy) {
        return new JVerifyTestRecord("", verifyByDefault, useBuiltinContracts, continueOnErrors,
                exitCode, new String[0], new String[0], -1, assertionsFailed, methodsVerified, -1,
                expectedToFail, blockedBy, false, "", -1, "");
    }
}
