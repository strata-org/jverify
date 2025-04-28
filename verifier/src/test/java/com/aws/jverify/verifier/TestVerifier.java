package com.aws.jverify.verifier;

import com.aws.jverify.common.AnnotatedRange;
import com.aws.jverify.common.Common;
import com.aws.jverify.common.Position;
import com.aws.jverify.common.Range;
import com.aws.jverify.common.TestMarkup;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import javax.tools.Diagnostic;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;

public class TestVerifier {
    private static final boolean IS_WINDOWS = System.getProperty("os.name", "").toLowerCase().contains("windows");

    private static final Path TEST_FILES_DIRECTORY = Path.of("./src/test/java/com/aws/jverify/verifier/tests");

    private static final Path EXAMPLES_DIRECTORY = Path.of("../examples/src/main/java/com/aws/verifier/examples");

    @Test
    public void javaError() throws IOException {
        var source = Common.getResourceFile(getClass(), "/JavaError.java");
        testMarkedSource(new SourceFile("JavaError.java", source));
    }
    
    
    @Test
    public void interfaces() throws IOException {
        testMarkedSource(TEST_FILES_DIRECTORY.resolve(Path.of("Interfaces.java")));
    }

    @TestFactory
    public Stream<DynamicTest> verifyTestFiles() throws IOException {
        //noinspection resource (JUnit will close the stream)
        return Files.walk(TEST_FILES_DIRECTORY)
                .filter(Files::isRegularFile)
                .map(path -> {
                    var name = TEST_FILES_DIRECTORY.relativize(path).toString();
                    return DynamicTest.dynamicTest(name, () -> testMarkedSource(path));
                });
    }

    @TestFactory
    public Stream<DynamicTest> verifyExampleFiles() throws IOException {
        //noinspection resource (JUnit will close the stream)
        return Files.walk(EXAMPLES_DIRECTORY)
                .filter(Files::isRegularFile)
                .map(path -> {
                    var name = EXAMPLES_DIRECTORY.relativize(path).toString();
                    return DynamicTest.dynamicTest(name, () -> testMarkedSource(path));
                });
    }

    @Test
    public void testRunThroughGradle() throws IOException, InterruptedException {
        var gradlePath = IS_WINDOWS ? "../gradlew.bat" : "../gradlew";
        var process = new ProcessBuilder(
                gradlePath,
                ":verifier:run",
                "--args=\"../examples/src/main/java/com/aws/verifier/examples/Fibonacci.java\"").start();
        var writer = new StringWriter();
        int exitCode;
        try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            reader.transferTo(writer);
            exitCode = process.waitFor();
        }
        var output = canonicalizeNewlines(writer.toString());
        assertThat(output, containsString("Dafny program verifier finished with 4 verified, 0 errors"));
        Assertions.assertEquals(0, exitCode);
    }

    /**
     * @see #testMarkedSource(SourceFile)
     */
    private static void testMarkedSource(Path markedSourcePath) throws IOException {
        var markedSource = Files.readString(markedSourcePath);
        testMarkedSource(new SourceFile(markedSourcePath, markedSource));
    }

    /**
     * Verifies the given source code and asserts that the exit code, emitted diagnostics,
     * and verified/error counts (from Dafny) match the specified values in the source code's test metadata.
     * See {@link #parseMetadata(String)} for details on the metadata format.
     */
    private static void testMarkedSource(SourceFile markedSourceFile) throws IOException {
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
                .map(TestVerifier::diagnosticAsAnnotatedRange)
                .sorted()
                .toList();
        var expectedAnnotations = parsedMarkup.ranges().stream().sorted().toList();

        assertThat("diagnostics", diagnosticsAsAnnotations, equalTo(expectedAnnotations));

        assertThat("exit code", verificationResults.getExitCode(), is(metadata.exitCode));
        assertThat("Dafny verified count", verificationResults.getDafnyVerifiedCount(), is(metadata.dafnyVerified));
        assertThat("Dafny error count", verificationResults.getDafnyErrorCount(), is(metadata.dafnyErrors));
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

    /**
     * Returns the text with all CRLF sequences replaced with LF.
     * This prevents erroneous failures of diff-based assertions on Windows platforms.
     */
    private static String canonicalizeNewlines(final String text) {
        return text.replaceAll("\r\n", "\n");
    }

    private static VerifierOptions getVerifierOptions() {
        var dafnyPath = Path.of("../dafny").toAbsolutePath()
                .resolve(IS_WINDOWS ? "Binaries/Dafny.exe" : "Scripts/dafny");
        var libraryJar = Path.of("../library/build/libs/library.jar");
        var prelude = Path.of("./src/main/resources/additional.dfy");
        return new VerifierOptions(dafnyPath, libraryJar, prelude,
                Path.of("../verifier/build/tmp/temp.dfy"),
                //null,
                null,
                true,
                new String[]{
                        "--use-basename-for-filename"
                        //,"--wait-for-debugger"
                }
        );
    }
}