package com.aws.jverify;

import com.aws.jverify.common.AnnotatedRange;
import com.aws.jverify.common.Common;
import com.aws.jverify.common.Position;
import com.aws.jverify.common.Range;
import com.aws.jverify.common.TestMarkup;
import com.aws.jverify.verifier.DafnyDiagnostic;
import com.aws.jverify.verifier.Driver;
import com.aws.jverify.verifier.SourceFile;
import com.aws.jverify.verifier.VerifierOptions;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.tools.Diagnostic;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;

public class TestVerifier {
    private static final boolean IS_WINDOWS = System.getProperty("os.name", "").toLowerCase().contains("windows");

    private static final Path TEST_FILES_DIRECTORY = Path.of("./src/test/java/com/aws/jverify");

    private static final Path EXAMPLES_DIRECTORY = Path.of("../examples/src/main/java/com/aws/verifier/examples");

    @Test
    public void javaError() throws IOException {
        var source = Common.getResourceFile(getClass(), "/JavaError.java");
        testMarkedSource(new SourceFile("JavaError.java", source));
    }

    // TODO: read from directory instead of hard-coding
    @ParameterizedTest
    @ValueSource(strings = {
            "AssertFalse.java",
            "ContractErrors.java",
            "FibonacciInvalid.java",
            "NoConstructor.java",
            "Operators.java",
            "ResolutionErrorsBooleanOperators.java",
            "ResolutionErrorsDoubleOperators.java",
            "ResolutionErrorsFloatOperators.java",
            "ResolutionErrorsIntegerOperators.java",
            "ResolutionErrorsNumericOperators.java",
            "ShouldVerify.java",
            "Switches.java",
            "TranslationErrors.java",
            "Types.java",
            "VerifyBooleanOperators.java",
            "VerifyNumericOperators.java",
            "VerifyStatements.java",
    })
    public void verifyTestFile(String fileName) throws IOException {
        testMarkedSource(TEST_FILES_DIRECTORY.resolve(fileName));
    }

    // Files are hard-coded for now since not all examples can be compiled and run as-is:
    //  - RuntimePreconditionExample: the try-catch can't be translated yet
    //  - BinarySearchProperty: the jqwik dependency needs to be added to the classpath
    //
    // TODO: read from directory instead of hard-coding
    @ParameterizedTest
    @ValueSource(strings = {
            "BinarySearch.java",
            "ExternalContract.java",
            "Fibonacci.java",
            "UserProfile.java",
    })
    public void verifyExampleFile(String fileName) throws IOException {
        testMarkedSource(EXAMPLES_DIRECTORY.resolve(fileName));
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

    private static final Pattern TEST_METADATA_PATTERN = Pattern.compile(
            "^// (?<ExitCodeLine>exitCode: (?<ExitCode>-?\\d+))$"
                    + "|^// (?<DafnyVerifiedLine>dafnyVerified: (?<DafnyVerified>\\d+))$"
                    + "|^// (?<DafnyErrorsLine>dafnyErrors: (?<DafnyErrors>\\d+))$",
            Pattern.MULTILINE
    );

    private record TestMetadata(int exitCode, Integer dafnyVerified, Integer dafnyErrors) {}

    /**
     * Parses test metadata from the given source content.
     * <p>
     * Note that if verification is expected to fail before invoking Dafny
     * (i.e. if there are errors in normal Java type-checking or during compilation to Dafny),
     * then the {@code dafnyVerified} and {@code dafnyErrors} metadata should not appear in the source code.
     * <p>
     * Example test metadata:
     * {@snippet :
     * // exitCode: 4
     * // dafnyVerified: 1
     * // dafnyErrors: 2
     *
     * class Foo {
     *     // ...
     * }
     * }
     */
    private static TestMetadata parseMetadata(String source) {
        Integer exitCode = null;
        Integer dafnyVerified = null;
        Integer dafnyErrors = null;
        var metadataMatcher = TEST_METADATA_PATTERN.matcher(source);
        while (metadataMatcher.find()) {
            if (metadataMatcher.group("ExitCodeLine") != null) {
                exitCode = Integer.parseInt(metadataMatcher.group("ExitCode"));
            } else if (metadataMatcher.group("DafnyVerifiedLine") != null) {
                dafnyVerified = Integer.parseInt(metadataMatcher.group("DafnyVerified"));
            } else if (metadataMatcher.group("DafnyErrorsLine") != null) {
                dafnyErrors = Integer.parseInt(metadataMatcher.group("DafnyErrors"));
            }
        }
        if (exitCode == null) {
            throw new AssertionError("Expected exit code not found in marked source");
        }
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
                //Path.of("../temp.dfy"),
                null,
                null,
                true,
                new String[]{
                        "--use-basename-for-filename"
                        //,"--wait-for-debugger"
                }
        );
    }
}