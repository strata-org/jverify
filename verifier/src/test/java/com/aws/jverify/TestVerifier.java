package com.aws.jverify;

import com.aws.jverify.common.Common;
import com.aws.jverify.common.TestMarkup;
import com.aws.jverify.verifier.Driver;
import com.aws.jverify.verifier.VerifierOptions;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.MatcherAssert.assertThat;

public class TestVerifier {
    private static final boolean IS_WINDOWS = System.getProperty("os.name", "").toLowerCase().contains("windows");

    @Test
    public void statements() throws IOException {
        verifyMarkedSourceFile("VerifyStatements.java", new DafnyResults(11, 0));
    }
    
    public void resolutionErrorBooleanTest() throws IOException {
        testMarkedSource(getTestFileContent("ResolutionErrorsBooleanOperators.java"),
                9, CommandLine.ExitCode.USAGE);
    }

    @Test
    public void verifyBooleanTest() throws IOException {
        verifyMarkedSourceFile("VerifyBooleanOperators.java", new DafnyResults(0, 1));
    }
    
    @Test
    public void resolutionErrorIntegerTest() throws IOException {
        testMarkedSource(getTestFileContent("ResolutionErrorsIntegerOperators.java"),
                19, CommandLine.ExitCode.USAGE);
    }
    
    @Test
    public void resolutionErrorNumericTest() throws IOException {
        testMarkedSource(getTestFileContent("ResolutionErrorsNumericOperators.java"),
                9, CommandLine.ExitCode.USAGE);
    }

    @Test
    public void resolutionErrorsDouble() throws IOException {
        testMarkedSource(getTestFileContent("ResolutionErrorsDoubleOperators.java"),
                20, CommandLine.ExitCode.USAGE);
    }
    
    @Test
    public void resolutionErrorsFloat() throws IOException {
        testMarkedSource(getTestFileContent("ResolutionErrorsFloatOperators.java"),
                20, CommandLine.ExitCode.USAGE);
    }

    @Test
    public void verifyNumericTest() throws IOException {
        verifyMarkedSourceFile("VerifyNumericOperators.java", new DafnyResults(0, 1));
    }
    
    @Test
    public void types() throws IOException {
        verifyMarkedSourceFile("Types.java", new DafnyResults(0, 3));
    }
    
    @Test
    public void shouldVerify() throws IOException {
        verifyMarkedSourceFile("ShouldVerify.java", new DafnyResults(0, 4));
    }
    
    @Test
    public void contractErrors() throws IOException {
        testMarkedSource(getTestFileContent("ContractErrors.java"), 
                3, CommandLine.ExitCode.USAGE);
    }
    
    @Test
    public void externalContract() throws IOException {
        StringWriter writer = new StringWriter();
        var exitCode = run("ExternalContract.java", true, writer);
        var output = canonicalizeNewlines(writer.toString());
        Assertions.assertEquals("""

Dafny program verifier finished with 2 verified, 0 errors
""", output);
        Assertions.assertEquals(0, exitCode);
    }
    
    @Test
    public void userProfile() throws IOException {
        StringWriter writer = new StringWriter();
        var exitCode = run("UserProfile.java", true, writer);
        var output = canonicalizeNewlines(writer.toString());
        Assertions.assertEquals("""
UserProfile.java(32:9-32:16): Error: a postcondition could not be proved on this return path
UserProfile.java(23:21-23:26): Related location: this is the postcondition that could not be proved
UserProfile.java(25:16-25:77): Related location: this proposition could not be proved

Dafny program verifier finished with 5 verified, 1 error
""", output);
        Assertions.assertEquals(4, exitCode);
    }

    @Test
    public void translationErrors() throws IOException {
        String markedSource = getTestFileContent("TranslationErrors.java");
        testMarkedSource(markedSource, 15, CommandLine.ExitCode.USAGE);
    }
    
    @Test
    public void javaError() throws IOException {
        var source = Common.getResourceFile(getClass(), "/JavaError.java");
        testMarkedSource(source, 1, CommandLine.ExitCode.USAGE);
    }
    
    @Test
    public void assertFalse() throws IOException {
        verifyMarkedSourceFile("AssertFalse.java", new DafnyResults(0, 1));
    }

    @Test
    public void fibonacciInvalid() throws IOException {
        verifyMarkedSourceFile("FibonacciInvalid.java", new DafnyResults(2, 4));
    }

    @Test
    public void operators() throws IOException {
        verifyMarkedSourceFile("Operators.java", new DafnyResults(9, 9));
    }

    @Test
    public void switches() throws IOException {
        verifyMarkedSourceFile("Switches.java", new DafnyResults(3, 3));
    }

    @Test
    public void fibonacciValid() throws IOException {
        StringWriter writer = new StringWriter();
        var exitCode = run("Fibonacci.java", true, writer);
        var output = canonicalizeNewlines(writer.toString());
        Assertions.assertEquals("\nDafny program verifier finished with 4 verified, 0 errors\n", output);
        Assertions.assertEquals(0, exitCode);
    }

    @Test
    public void binarySearchValid() throws IOException {
        StringWriter writer = new StringWriter();
        var exitCode = run("BinarySearch.java", true, writer);
        var output = canonicalizeNewlines(writer.toString());
        Assertions.assertEquals("""

Dafny program verifier finished with 3 verified, 0 errors
""",
                output
        );
        Assertions.assertEquals(0, exitCode);
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
            reader.close();
        }
        var output = canonicalizeNewlines(writer.toString());
        Assertions.assertTrue(output.contains("Dafny program verifier finished with 4 verified, 0 errors"));
        Assertions.assertEquals(0, exitCode);
    }

    @Test
    public void testNoConstructor() throws IOException, InterruptedException {
        StringWriter writer = new StringWriter();
        var exitCode = run("NoConstructor.java", false, writer);
        Assertions.assertEquals(0, exitCode);
    }

    record DafnyResults(int successCount, int errorCount) {}
    private void verifyMarkedSourceFile(String inputFileName, DafnyResults dafnyResults) throws IOException {
        testMarkedSourceVerification(getTestFileContent(inputFileName), dafnyResults);
    }

    private static String getTestFileContent(String inputFileName) throws IOException {
        var directory = Path.of("./src/test/java/com/aws/jverify");
        var filePath = directory.resolve(inputFileName);
        return Files.readString(filePath);
    }

    private void testMarkedSourceVerification(String markedSource, DafnyResults dafnyResults) throws IOException {
        int expectedExitCode = dafnyResults.errorCount() > 0 ? 4 : 0;
        var output = testMarkedSource(markedSource, dafnyResults.errorCount(), expectedExitCode);
        var pluralization = dafnyResults.errorCount() != 1 ? "s" : "";
        String ending = "Dafny program verifier finished with " +
                dafnyResults.successCount() + " verified, " +
                dafnyResults.errorCount() + " error" + pluralization + "\n";
        assertThat(output, endsWith(ending));
    }

    /**
     * Verifies the given source code, asserting that each markup annotation appears in the output,
     * and that the exit code and number of matched error annotations are as expected.
     * <p>
     * NOTE: Errors that appear in the verification output, but which don't appear in the marked-up source code,
     * are currently silently ignored.
     */
    private static String testMarkedSource(String markedSource, int expectedErrorCount, int expectedExitCode) throws IOException {
        StringWriter writer = new StringWriter();
        var options = getVerifierOptions();
        var result = TestMarkup.getPositionsAndAnnotatedRanges(markedSource);
        var source = result.output();
        var exitCode = Driver.verifyJavaSource(options, source, writer);
        var output = canonicalizeNewlines(writer.toString());
        var errorsFound = 0;
        for(var range : result.ranges()) {
            var positionString = "(" + range.range.toString() + ")";
            String expectation = positionString + ": " + range.annotation;
            if (range.annotation.startsWith("Error:") || range.annotation.startsWith("error: ")) {
                errorsFound++;
            }

            assertThat(output, containsString(expectation));
        }
        Assertions.assertEquals(expectedErrorCount, errorsFound,
                () -> "Mismatched error count in output:\n%s".formatted(output));
        Assertions.assertEquals(expectedExitCode, exitCode,
                () -> "Mismatched exit code; output:\n%s".formatted(output));
        return output;
    }

    /**
     * Returns the text with all CRLF sequences replaced with LF.
     * This prevents erroneous failures of diff-based assertions on Windows platforms.
     */
    private static String canonicalizeNewlines(final String text) {
        return text.replaceAll("\r\n", "\n");
    }

    private int run(String inputFileName, boolean fromExamples, Writer writer) throws IOException {
        var directory = fromExamples
                ? Path.of("../examples/src/main/java/com/aws/verifier/examples")
                : Path.of("./src/test/java/com/aws/jverify");
        var filePath = directory.resolve(inputFileName);
        var options = getVerifierOptions();
        return Driver.verifyJavaExample(options, filePath, writer);
    }

    private static VerifierOptions getVerifierOptions() {
        var dafnyPath = Path.of("../dafny").toAbsolutePath()
                .resolve(IS_WINDOWS ? "Binaries/Dafny.exe" : "Scripts/dafny");
        var libraryJar = Path.of("../library/build/libs/library.jar");
        var prelude = Path.of("./src/main/resources/additional.dfy");
        return new VerifierOptions(dafnyPath, libraryJar, prelude, 
                //Path.of("../temp.dfy"),
                null,
                null, true,
                new String[] {
                        "--use-basename-for-filename"
                        //,"--wait-for-debugger"
                }
        );
    }
}