package org.strata.jverify.testengine;

import org.strata.jverify.common.AnnotatedRange;
import org.strata.jverify.common.Position;
import org.strata.jverify.common.Range;
import org.strata.jverify.verifier.*;
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
import java.util.ArrayList;
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

    // Must agree with the version set in Config (`StrataJavaFrontEnd-1.0`) and
    // build.gradle.kts (`version = "1.0"`), which govern the jar names under
    // build/lib/ that getVerifierOptions() below resolves.
    private static final String PKG_VERSION = "1.0";

    /**
     * Anchor range for a diagnostic reported against a file other than the one
     * under test. Such a diagnostic carries its real range inside its annotation
     * text ({@code path(range) message}), so the range itself is fixed — it only
     * has to be stable and identical on both the expected and actual side.
     */
    private static final Range CROSS_FILE_RANGE = new Range(new Position(1, 1), new Position(1, 2));

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
     * and verified/error counts match the specified values in the source code's test metadata.
     */
    public static void testMarkedSource(SourceFile markedSourceFile, JVerifyTest annotation) throws IOException {
        var parsedMarkup = TestMarkup.getPositionsAndAnnotatedRanges(markedSourceFile.getCharContent(false));
        List<AnnotatedRange> ranges = new ArrayList<>(parsedMarkup.ranges());
        ranges.addAll(additionalFileRanges(markedSourceFile, annotation));
        ranges.addAll(contractFileRanges(markedSourceFile, annotation));
        verifyFile(markedSourceFile, annotation, ranges);
    }

    /**
     * Expected diagnostics declared by markup inside the {@code .java} files of
     * the test's {@code contractSourcePath} directories, in the
     * {@code path(range) message} cross-file form. A contract file is named
     * {@code string://<path relative to its directory>}, as both frontends name
     * directory contract sources, so its label is that URI's path.
     */
    public static List<AnnotatedRange> contractFileRanges(SourceFile markedSourceFile, JVerifyTest annotation)
            throws IOException {
        List<AnnotatedRange> ranges = new ArrayList<>();
        for (Path dir : contractSourcePath(markedSourceFile, annotation)) {
            if (!Files.isDirectory(dir)) {
                continue;
            }
            List<Path> files;
            try (var walk = Files.walk(dir)) {
                files = walk.filter(p -> p.toString().endsWith(".java")).sorted().toList();
            }
            for (Path file : files) {
                var parsed = TestMarkup.getPositionsAndAnnotatedRanges(Files.readString(file));
                var label = URI.create("string://" + dir.relativize(file).toString().replace('\\', '/'))
                        .getPath();
                for (AnnotatedRange range : parsed.ranges()) {
                    ranges.add(new AnnotatedRange(label + "(" + range.range() + ") " + range.annotation(),
                            CROSS_FILE_RANGE));
                }
            }
        }
        return ranges;
    }

    /** The test's {@code contractSourcePath} entries, resolved against the test file's directory. */
    public static List<Path> contractSourcePath(SourceFile markedSourceFile, JVerifyTest annotation) {
        var parent = Path.of(markedSourceFile.toUri()).getParent();
        return Arrays.stream(annotation.contractSourcePath())
                .map(entry -> parent.resolve(entry).normalize())
                .toList();
    }

    /** The builtin contracts when the test uses them, then the test's own {@code contractSourcePath}. */
    public static List<Path> contractSources(SourceFile markedSourceFile, JVerifyTest annotation) {
        var out = new ArrayList<Path>();
        if (annotation.useBuiltinContracts()) {
            out.add(getBuiltinContractsSourceDir());
        }
        out.addAll(contractSourcePath(markedSourceFile, annotation));
        return out;
    }

    /**
     * Expected diagnostics declared by markup inside {@code additionalFiles}.
     *
     * <p>Carets in an additional file annotate ranges in that file, so they are
     * rewritten into the {@code path(range) message} form that
     * {@link #diagnosticAsAnnotatedRange} produces for a diagnostic outside the
     * file under test. This lets a multi-file test annotate each failure in the
     * file that causes it, instead of restating them all in the marked file.
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

    public static void verifyFile(SourceFile sourceFile, JVerifyTest annotation, List<AnnotatedRange> ranges) throws IOException {
        verifyFile(sourceFile, annotation, ranges,
                getVerifierOptions(annotation, null, contractSources(sourceFile, annotation)));
    }

    public static void verifyFile(SourceFile sourceFile, JVerifyTest annotation, List<AnnotatedRange> ranges,
                                  VerifierOptions options) throws IOException {
        // The fixture's annotation states what a correct frontend produces; this
        // frontend's own known gaps are recorded per class in source-xfails.txt
        // (SourceXfailManifest), mirroring the bytecode frontend's manifest: a forced
        // XFAIL inverts the outcome, a RUN entry runs an annotation-level xfail.
        String fqcn = fqcnOf(sourceFile);
        var manifest = SourceXfailManifest.get();
        var forcedXfail = fqcn == null ? null : manifest.forcedXfail(fqcn);
        boolean forcedRun = fqcn != null && manifest.isForcedRun(fqcn);
        boolean expectedToFail = forcedXfail != null || (annotation.expectedToFail() && !forcedRun);

        // skip= and expectedToFail are mutually exclusive: skip is evaluated first, so a
        // test with both would abort as skipped and never reach the XPASS tripwire. Fail
        // loud with a non-AssertionError (the xfail branch below swallows AssertionError).
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
                ? "source-xfails.txt " + forcedXfail.blocker() + ": " + forcedXfail.reason()
                : describeBlockers(annotation);
        applyXfailInversion(why, () -> runAndCheck(sourceFile, annotation, ranges, options));
    }

    /**
     * The fully-qualified class name a test source declares: its {@code package} plus
     * the file's name. Null when the source names no package, which no corpus fixture
     * does.
     */
    private static String fqcnOf(SourceFile sourceFile) {
        String name = Path.of(sourceFile.toUri()).getFileName().toString();
        if (!name.endsWith(".java")) {
            return null;
        }
        String content = sourceFile.getCharContent(false).toString();
        var m = java.util.regex.Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)\\s*;").matcher(content);
        return m.find() ? m.group(1) + "." + name.substring(0, name.length() - ".java".length()) : null;
    }

    /** A check that may throw the checked exceptions {@link #runAndCheck} does. */
    @FunctionalInterface
    public interface VerificationCheck {
        void run() throws IOException;
    }

    /**
     * Run an expected-to-fail {@code check} and invert its outcome. Split out of
     * {@link #verifyFile} so the inversion -- the XPASS tripwire that is the point
     * of the scheme -- is unit-testable with a canned check instead of only through
     * a full backend run (XpassInversionTest).
     *
     * <p>Still throwing an {@link AssertionError} means the blocker(s) have not
     * cleared: report as skipped so the build stays green. Only AssertionError is an
     * "expected failure"; a genuine infra error (IOException, or a RuntimeException
     * from the driver/Strata process) is NOT caught here and propagates, so an xfail
     * test that stops *running* fails loudly instead of masquerading as still-broken.
     * Completing with no failure means the blocker(s) cleared -> fail loudly (XPASS).
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
            : java.util.Arrays.toString(annotation.blockedBy());
    }

    private static void runAndCheck(SourceFile sourceFile, JVerifyTest annotation, List<AnnotatedRange> ranges,
                                  VerifierOptions options) throws IOException {

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
        checkResults(results, sourceFile, annotation, ranges);
    }

    /**
     * A NoVerdict run ({@link JVerifyResults.NoVerdict}) reached a test with
     * verification-count expectations -- a front-end usage error. An
     * {@link AssertionError} subtype so an {@code expectedToFail} test swallows it while a
     * normal test fails loud; see {@link JVerifyResults} for why this differs from the
     * non-swallowable {@link JVerifyResults.DidNotRun} (backend crash) path.
     */
    public static final class NoVerdictExpectation extends AssertionError {
        NoVerdictExpectation(String message) { super(message); }
    }

    /**
     * Assert a verification {@code results} against the test's expectations. Split out
     * of {@link #runAndCheck} so the loud-failure guards (DidNotRun / NoVerdict-with-count)
     * are unit-testable with a canned result. Public so cross-module tests can call it.
     */
    public static void checkResults(JVerifyResults results, SourceFile sourceFile,
                                    JVerifyTest annotation, List<AnnotatedRange> ranges) throws IOException {
        // Infra failure (see JVerifyResults.DidNotRun), not a verification outcome:
        // fail LOUD with a non-AssertionError so an xfail test can't swallow it.
        if (results instanceof JVerifyResults.DidNotRun) {
            throw new IllegalStateException("Strata backend did not run (exit "
                + results.exitCode() + "): infrastructure failure, not a verification result.");
        }

        var diagnosticsAsAnnotations = results.diagnostics().stream()
                .map(d -> diagnosticAsAnnotatedRange(sourceFile.toUri(), d))
                .sorted()
                .toList();

        if (Boolean.parseBoolean(System.getenv("JVERIFY_UPDATE_TEST_ANNOTATIONS"))) {
            if (results.exitCode() == 0 || results.exitCode() == 4) {
                updateTestAnnotation(sourceFile, annotation, results);
            }
        }

        VerificationResults vr = results.verdictOrNull();
        Integer expectedJavaVerifiedCount = annotation.methodsVerified() >= 0 ? annotation.methodsVerified() : null;
        Integer expectedMethodsInvalidCount = annotation.methodsInvalid() >= 0 ? annotation.methodsInvalid() : null;
        Integer expectedErrorCount = annotation.errorCount() >= 0 ? annotation.errorCount() : null;
        Integer expectedJavaSkippedCount = annotation.methodsSkipped() >= 0 ? annotation.methodsSkipped() : null;
        // NoVerdict + a count expectation: throw NoVerdictExpectation (see its javadoc --
        // an AssertionError subtype, so a normal test fails loud but an xfail swallows it).
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
            // Diagnostics parsed from Strata's output carry no JavaFileObject, so
            // getSource() is null; their file is on the diagnostic itself. Without
            // this, every such diagnostic looked like it came from the file under
            // test and one in an additionalFiles entry could not be annotated there.
            sourceUri = withRange.getUri();
        }
        var startPos = new Position(diagnostic.getLineNumber(), diagnostic.getColumnNumber());
        var endPos = diagnostic instanceof DiagnosticWithRange diagnosticWithRange
                ? new Position(diagnosticWithRange.getRange().end().line(), diagnosticWithRange.getRange().end().character())
                : new Position(startPos.line(), startPos.character() + 1);
        var range = new Range(startPos, endPos);
        if (sourceUri == null || sourceUri.equals(testFile.normalize())) {
            return new AnnotatedRange(Driver.formatMessage(diagnostic), range);
        } else {
            String path;
            if (sourceUri.getScheme().equals("string")) {
                path = sourceUri.getPath();
            } else {
                path = testFile.resolve(".").relativize(sourceUri).getPath();
            }
            return new AnnotatedRange(path + "(" + range + ") " + Driver.formatMessage(diagnostic), CROSS_FILE_RANGE);
        }
    }

    public static VerifierOptions getVerifierOptions(JVerifyTest annotation, PositionFilter positionFilter) {
        if (annotation.contractSourcePath().length > 0) {
            throw new IllegalArgumentException("contractSourcePath is relative to the test file;"
                    + " pass the resolved contractSources(...)");
        }
        return getVerifierOptions(annotation, positionFilter,
                annotation.useBuiltinContracts() ? List.of(getBuiltinContractsSourceDir()) : List.of());
    }

    /** Verifier options for {@code annotation}, with {@code contractSources} as the contractSourcePath. */
    public static VerifierOptions getVerifierOptions(JVerifyTest annotation, PositionFilter positionFilter,
                                                     List<Path> contractSources) {
        // Resolved via `strata` on the build-tool PATH (StrataCLI in Config); no explicit path needed.
        Path backendPath = null;
        var buildLib = Path.of("../build/lib");
        var libraryJar = buildLib.resolve("library-" + PKG_VERSION + ".jar");
        var verifierJar = buildLib.resolve("verifier-" + PKG_VERSION + ".jar");
        var libraryForTestingClassPath = buildLib.resolve("library-for-testing-" + PKG_VERSION + ".jar");
        var testEngineClassPath = Path.of("../test-engine/build/classes/java/main").toAbsolutePath();
        var workingDirectory = Path.of(System.getProperty("user.dir"));
        return new VerifierOptions(new PrintWriter(System.out),
                workingDirectory,
                backendPath,
                List.of(verifierJar, libraryJar, testEngineClassPath, libraryForTestingClassPath),
                Path.of("../build/temp.laurel"),
                false,
                true,
                contractSources,
                true,
                annotation.verifyByDefault(),
                annotation.continueOnErrors(),
                positionFilter, true, false,
                annotation.interpret(),
                annotation.entry().isEmpty() ? null : annotation.entry(),
                annotation.fuel() > 0 ? annotation.fuel() : null
        );
    }

    public static Path getBuiltinContractsSourceDir() {
        return Path.of("../builtin-contracts/src/main/java").toAbsolutePath();
    }

    /**
     * For creating a JVerifyTest annotation without having it in source code.
     * Useful for testing things like examples where we don't want the explicit annotation.
     */
    public static JVerifyTest makeJVerifyTestAnnotation(int methodsVerified, int assertionsFailed) {
        return makeJVerifyTestAnnotation(true, assertionsFailed > 0 ? 4 : 0, methodsVerified, assertionsFailed,
                false, true);
    }

    /**
     * For creating a JVerifyTest annotation without having it in source code.
     * Useful for testing things like examples where we don't want the explicit annotation.
     */
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
                expectedToFail, blockedBy, false, "", -1);
    }

    public static void updateTestAnnotation(SourceFile sourceFile, JVerifyTest annotation, JVerifyResults results) throws IOException {
        try (BufferedReader reader = new BufferedReader(sourceFile.openReader(false))) {
            var allLines = reader.lines().toArray(String[]::new);
            var maybeAnnotationIndex = IntStream.range(0, allLines.length)
                    .filter(index -> allLines[index].startsWith("@JVerifyTest"))
                    .findFirst();
            if (maybeAnnotationIndex.isPresent()) {
                int annotationIndex = maybeAnnotationIndex.getAsInt();
                var verdict = results.verdictOrNull();
                var uvr = verdict != null ? verdict : new VerificationResults(0, 0, 0, 0);
                var newLine = allLines[annotationIndex].
                        replaceFirst("exitCode = \\d", "exitCode = " + results.exitCode()).
                        replaceFirst("javaVerified = \\d+", "javaVerified = " + uvr.verificationPassedMethods()).
                        replaceFirst("javaErrors = \\d+", "javaErrors = " + uvr.verificationFailedMethods());
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
