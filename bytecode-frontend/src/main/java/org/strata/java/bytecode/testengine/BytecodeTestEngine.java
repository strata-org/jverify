package org.strata.java.bytecode.testengine;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;
import org.strata.jverify.testengine.JVerifyTestRecord;
import org.strata.jverify.testengine.TestMarkup;

import org.strata.jverify.common.AnnotatedRange;
import org.strata.jverify.common.Position;
import org.strata.jverify.common.Range;
import org.strata.jverify.verifier.DiagnosticWithRange;
import org.strata.jverify.verifier.JVerifyResults;
import org.strata.jverify.verifier.SourceFile;
import org.strata.jverify.verifier.VerificationResults;
import org.strata.java.bytecode.verifier.VerifierDriver;
import org.strata.java.bytecode.verifier.VerifierOptions;
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
import java.io.PrintWriter;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * JUnit Platform engine that runs JVerify-style verification tests through
 * the bytecode frontend. Port of StrataJavaFrontEnd's {@code JVerifyTestEngine}
 * with the javac driver swapped for {@code VerifierDriver} (javac -g -Xjcov ->
 * decompile -> Laurel -> Strata); discovery, markup matching, xfail
 * inversion, and expectation checking are unchanged.
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
        // JFE's own `:verifier:test` too. It stays inert unless activated by
        // `bytecode.corpus.root` (set only by the corpusTest task), so JFE need
        // not know it exists -- discovering nothing here means JUnit never
        // executes it, and the @JVerifyTest fixtures run only under JFE's
        // own jverify-test-engine.
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
                    descriptor.addChild(new JVerifyTestDescriptor(id, testClass));
                });
        return descriptor;
    }

    private boolean isJVerifyTest(Class<?> clazz) {
        return clazz.isAnnotationPresent(JVerifyTest.class);
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

            // The verification corpus is owned by StrataJavaFrontEnd and
            // shared verbatim; its source root is supplied by the build via
            // the bytecode.corpus.root property (falls back to this project's
            // own src/test/java, e.g. when a fixture lives locally).
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
     * Root of the shared verification corpus. Supplied by the build via the
     * {@code bytecode.corpus.root} system property (the StrataJavaFrontEnd test
     * source tree); falls back to this project's own {@code src/test/java}.
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

    /** Bytecode-frontend xfail overrides for the shared corpus (loaded once). */
    private static volatile BytecodeXfailManifest xfailManifest;

    static BytecodeXfailManifest xfailManifest() {
        var m = xfailManifest;
        if (m == null) {
            m = BytecodeXfailManifest.load();
            xfailManifest = m;
        }
        return m;
    }

    /**
     * @see #testMarkedSource(SourceFile, JVerifyTest, String)
     */
    public static void testMarkedSource(Path markedSourcePath, JVerifyTest annotation, String className)
            throws IOException {
        var markedSource = Files.readString(markedSourcePath);
        testMarkedSource(new SourceFile(markedSourcePath, markedSource), annotation, className);
    }

    /**
     * Verifies the given source code and asserts that the exit code, emitted
     * diagnostics, and verified/error counts match the values in the source
     * code's test metadata. {@code className} is the fully-qualified name of
     * the test class, used to consult the bytecode-frontend xfail manifest.
     */
    public static void testMarkedSource(SourceFile markedSourceFile, JVerifyTest annotation, String className)
            throws IOException {
        var parsedMarkup = TestMarkup.getPositionsAndAnnotatedRanges(markedSourceFile.getCharContent(false));
        List<AnnotatedRange> ranges = new ArrayList<>(parsedMarkup.ranges());
        ranges.addAll(additionalFileRanges(markedSourceFile, annotation));
        verifyFile(markedSourceFile, annotation, ranges, className);
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

    public static void verifyFile(SourceFile sourceFile, JVerifyTest annotation, List<AnnotatedRange> ranges,
                                  String className)
            throws IOException {
        verifyFile(sourceFile, annotation, ranges, getVerifierOptions(annotation), className);
    }

    public static void verifyFile(SourceFile sourceFile, JVerifyTest annotation, List<AnnotatedRange> ranges,
                                  VerifierOptions options, String className) throws IOException {
        var manifest = xfailManifest();
        var forcedXfail = manifest.forcedXfail(className);
        boolean forcedRun = manifest.isForcedRun(className);

        // The shared corpus is owned by StrataJavaFrontEnd; its annotation
        // records the SOURCE frontend's outcome. For the bytecode frontend the
        // manifest overrides that decision per class:
        //   - a forced-xfail entry makes a normally-passing test an xfail
        //     (known bytecode gap the shared annotation does not flag);
        //   - a forced-run entry runs an annotation-level expectedToFail test
        //     normally (the source-frontend gap it records does not apply).
        boolean expectedToFail = forcedXfail != null || (annotation.expectedToFail() && !forcedRun);

        // skip= and expectedToFail are mutually exclusive: skip is evaluated
        // first, so a test with both would abort as skipped and never reach
        // the XPASS tripwire.
        if (expectedToFail && annotation.skip() != null && !annotation.skip().isEmpty()) {
            throw new IllegalStateException("@JVerifyTest sets both skip and expectedToFail; "
                    + "these are mutually exclusive -- clear skip so the xfail/XPASS check runs.");
        }
        // Legacy hard skip: still supported, but discouraged. Prefer expectedToFail.
        Assumptions.assumeTrue(annotation.skip() == null || annotation.skip().isEmpty(), annotation.skip());

        if (!expectedToFail) {
            runAndCheck(sourceFile, annotation, ranges, options);
            return;
        }
        // xfail: run it, but invert the outcome.
        String why = forcedXfail != null
                ? forcedXfail.blocker() + ": " + forcedXfail.reason()
                : describeBlockers(annotation);
        applyXfailInversion(why,
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
     * manifest entry be removed.
     */
    public static void applyXfailInversion(String why, VerificationCheck check) throws IOException {
        try {
            check.run();
        } catch (AssertionError expected) {
            Assumptions.assumeTrue(false, "Expected failure (" + why + "): " + expected.getMessage());
            return;
        }
        Assertions.fail("XPASS: this test is marked expectedToFail (" + why + ") but now PASSES. "
                + "Remove expectedToFail/blockedBy and adopt it as a normal test.");
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

    /**
     * Assert a verification {@code results} against the test's expectations.
     */
    public static void checkResults(JVerifyResults results, SourceFile sourceFile,
                                    JVerifyTest annotation, List<AnnotatedRange> ranges) {
        // Infra failure, not a verification outcome: fail LOUD with a
        // non-AssertionError so an xfail test can't swallow it.
        if (results instanceof JVerifyResults.DidNotRun) {
            throw new IllegalStateException("Strata backend did not run (exit "
                    + results.exitCode() + "): infrastructure failure, not a verification result.");
        }

        var diagnosticsAsAnnotations = results.diagnostics().stream()
                .map(d -> diagnosticAsAnnotatedRange(sourceFile.toUri(), d))
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

        var expectedAnnotations = ranges.stream().sorted().toList();
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
        // Classpath for the -g -Xjcov compile of the test sources. The spec
        // library must be the CLASS-retention variant (bytecode-frontend-lib.jar, passed
        // by the corpusTest task) -- the JVerify class LOADED in this JVM is
        // JFE's SOURCE-retention :library, whose annotations would not
        // survive into the classfiles the decompiler reads. The test-engine
        // and library-for-testing entries are position-independent and come
        // from the loaded classes' code sources.
        var libraryClassPath = Path.of(java.util.Objects.requireNonNull(
                System.getProperty("bytecode.library.jar"),
                "bytecode.library.jar system property not set; run via the corpusTest task"));
        var testEngineClassPath = codeSourceOf(JVerifyTest.class);
        var libraryForTestingClassPath = codeSourceOf(org.strata.jverify.testing.LibraryClassA.class);
        var workingDirectory = Path.of(System.getProperty("user.dir"));
        return new VerifierOptions(new PrintWriter(System.out),
                workingDirectory,
                List.of(libraryClassPath, testEngineClassPath, libraryForTestingClassPath),
                null,
                false,
                true,
                List.of(), // builtin contracts: not yet supported by the bytecode frontend
                true,
                annotation.verifyByDefault(),
                annotation.continueOnErrors(),
                true,
                false,
                annotation.interpret(),
                annotation.entry().isEmpty() ? null : annotation.entry(),
                annotation.fuel() > 0 ? annotation.fuel() : null
        );
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
                exitCode, new String[0], -1, assertionsFailed, methodsVerified, -1,
                expectedToFail, blockedBy, false, "", -1);
    }
}
