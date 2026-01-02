package com.aws.jverify.testengine;

import com.aws.jverify.common.AnnotatedRange;
import com.aws.jverify.common.Position;
import com.aws.jverify.common.Range;
import com.aws.jverify.verifier.*;
import com.aws.jverify.verifier.dafny.*;
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
        for(var backend : annotation.BACKENDS()) {
            var results = IDriver.getDriver(backend).verifyJavaFiles(inputs, options);

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
            Integer expectedFailedAssertionsCount = annotation.methodsInvalid() >= 0 ? annotation.failedAssertions() : null;
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
                        if (expectedFailedAssertionsCount != null) {
                            assertThat("Failed assertions count",
                                    results.verificationResults().verificationFailedAssertions(),
                                    is(expectedFailedAssertionsCount));
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

            if (annotation.verifyPrintedDafny()) {
                verifyPrintedDafny(results, options);
            }
        }
    }

    private static void verifyPrintedDafny(JVerifyResults previousResults, VerifierOptions verifierOptions)
            throws IOException {
        boolean jverifyCompilationFailed = previousResults.exitCode() == 2;
        if (jverifyCompilationFailed) {
            return;
        } else if (verifierOptions.printDafny() == null) {
            throw new RuntimeException("");
        }

        var processBuilder = new ProcessBuilder(
                verifierOptions.dafnyPath().toString(),
                "verify",
                verifierOptions.printDafny().toString(),
                "--allow-axioms",
                "--type-system-refresh",
                "--general-newtypes",
                "--general-traits=datatype"
        );
        var process = processBuilder.redirectErrorStream(true).start();
        try(var stdout = process.inputReader()) {
            var dafnyExitCode = process.waitFor();
            var exitCode = Driver.getExitCodeFromDafny(dafnyExitCode);
            String content = readerToString(stdout);
            Assertions.assertEquals(previousResults.exitCode(), exitCode, content);
        } catch (InterruptedException e) {
            Assertions.fail();
        }
    }

    private static String readerToString(BufferedReader stdout) throws IOException {
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = stdout.readLine()) != null) {
            sb.append(line).append("\n");
        }
        return sb.toString();
    }

    @Nullable
    private static AnnotatedRange diagnosticAsAnnotatedRange(URI testFile, Diagnostic<?> diagnostic) {
        var source = diagnostic.getSource();
        URI sourceUri = null;
        if (source instanceof URI uri) {
            sourceUri = uri;
        } else if (source instanceof SourceFile javaFileObject) {
            sourceUri = javaFileObject.toUri();
        }
        var startPos = new Position(diagnostic.getLineNumber(), diagnostic.getColumnNumber());
        var endPos = diagnostic instanceof DafnyDiagnostic dafnyDiagnostic
                ? new Position(dafnyDiagnostic.getEndLineNumber(), dafnyDiagnostic.getEndColumnNumber())
                : new Position(startPos.line(), startPos.character() + 1);
        var range = new Range(startPos, endPos);
        if (sourceUri == null || sourceUri.equals(testFile.normalize())) {
            return new AnnotatedRange(IDriver.formatMessage(diagnostic), range);
        } else {
            Range zeroRange = new Range(new Position(1, 1), new Position(1, 2));
            String path;
            if (sourceUri.getScheme().equals("string")) {
                path = sourceUri.getPath();
            } else {
                path = testFile.resolve(".").relativize(sourceUri).getPath();
            }
            return new AnnotatedRange(path + "(" + range + ") " + IDriver.formatMessage(diagnostic), zeroRange);
        }
    }

    private static final boolean IS_WINDOWS = System.getProperty("os.name", "").toLowerCase().contains("windows");

    public static VerifierOptions getVerifierOptions(JVerifyTest annotation, PositionFilter positionFilter) {
        var dafnyPath = getDafnyInSubmodulePath();
        var libraryJar = Path.of("../library/build/libs/library-1.0-SNAPSHOT.jar");
        var libraryForTestingClassPath = Path.of("../library-for-testing/build/libs/library-for-testing-1.0-SNAPSHOT.jar");
        var builtinContracts = getBuiltinContractsSourceDir();
        var testEngineClassPath = Path.of("../test-engine/build/classes/java/main").toAbsolutePath();
        var workingDirectory = Path.of(System.getProperty("user.dir"));
        var prelude = Path.of("../verifier/src/main/resources/additional.dfy");
        return new VerifierOptions(new PrintWriter(System.out),
                workingDirectory,
                dafnyPath,
                List.of(libraryJar, testEngineClassPath, libraryForTestingClassPath),
                prelude,
                false,
                Path.of("../build/temp.dfy"),
                Path.of("../build/temp.dbin"),
                true,
                annotation.useBuiltinContracts() ? List.of(builtinContracts) : List.of(),
                true,
                new String[] {
                        "--use-basename-for-filename",
                        //"--wait-for-debugger",
                },
                annotation.verifyByDefault(),
                annotation.continueOnErrors(),
                positionFilter, true, false
        );
    }

    public static Path getDafnyInSubmodulePath() {
        return Path.of("../dafny").toAbsolutePath()
                .resolve(IS_WINDOWS ? "Binaries/Dafny.exe" : "Scripts/dafny");
    }

    public static Path getBuiltinContractsSourceDir() {
        return Path.of("../builtin-contracts/src/main/java").toAbsolutePath();
    }

    /**
     * For creating a JVerifyTest annotation without having it in source code.
     * Useful for testing things like examples where we don't want the explicit annotation.
     */
    public static JVerifyTest makeJVerifyTestAnnotation(int methodsVerified, int assertionsFailed) {
        return makeJVerifyTestAnnotation(true, assertionsFailed > 0 ? 4 : 0, methodsVerified, assertionsFailed, false, false, true);
    }
    
    /**
     * For creating a JVerifyTest annotation without having it in source code.
     * Useful for testing things like examples where we don't want the explicit annotation.
     */
    public static JVerifyTest makeJVerifyTestAnnotation(boolean verifyByDefault, int exitCode,
                                                        int methodsVerified, int assertionsFailed,
                                                        boolean verifyPrintedDafny,
                                                        boolean continueOnErrors,
                                                        boolean useBuiltinContracts) {
        return new JVerifyTestRecord("", verifyByDefault, useBuiltinContracts, continueOnErrors, 
                exitCode, new String[0], verifyPrintedDafny, -1, assertionsFailed, methodsVerified, -1, 
                new Backend[]{ Backend.Dafny }, new int[] {});
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

