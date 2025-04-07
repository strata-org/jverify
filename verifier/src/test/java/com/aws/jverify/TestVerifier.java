package com.aws.jverify;

import com.aws.jverify.common.Common;
import com.aws.jverify.common.TestMarkup;
import com.aws.jverify.verifier.Driver;
import com.aws.jverify.verifier.VerifierOptions;
import org.checkerframework.checker.nullness.qual.Nullable;
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
    public void types() throws IOException {
        testMarkedSourceFile("Types.java", new DafnyResults(6, 3));
    }
    
    @Test
    public void shouldVerify() throws IOException {
        testMarkedSourceFile("ShouldVerify.java", new DafnyResults(2, 4));
    }
    
    @Test
    public void contractErrors() throws IOException {
        testMarkedSourceFile("ContractErrors.java", null);
    }
    
    @Test
    public void externalContract() throws IOException {
        StringWriter writer = new StringWriter();
        var exitCode = run("ExternalContract.java", true, writer);
        var output = canonicalizeNewlines(writer.toString());
        Assertions.assertEquals("""

Dafny program verifier finished with 4 verified, 0 errors
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

Dafny program verifier finished with 7 verified, 1 error
""", output);
        Assertions.assertEquals(4, exitCode);
    }

    @Test
    public void translationErrors() throws IOException {
        testMarkedSourceFile("TranslationErrors.java", null);
    }
    
    @Test
    public void javaError() throws IOException {
        var source = Common.getResourceFile(getClass(), "/JavaError.java");
        testMarkedSource(source, null);
    }
    
    @Test
    public void assertFalse() throws IOException {
        testMarkedSourceFile("AssertFalse.java", new DafnyResults(2, 1));
    }

    @Test
    public void fibonacciInvalid() throws IOException {
        testMarkedSourceFile("FibonacciInvalid.java", new DafnyResults(4, 4));
    }

    @Test
    public void operators() throws IOException {
        testMarkedSourceFile("Operators.java", new DafnyResults(11, 9));
    }

    @Test
    public void fibonacciValid() throws IOException {
        StringWriter writer = new StringWriter();
        var exitCode = run("Fibonacci.java", true, writer);
        var output = canonicalizeNewlines(writer.toString());
        Assertions.assertEquals("\nDafny program verifier finished with 6 verified, 0 errors\n", output);
        Assertions.assertEquals(0, exitCode);
    }

    @Test
    public void binarySearchValid() throws IOException {
        StringWriter writer = new StringWriter();
        var exitCode = run("BinarySearch.java", true, writer);
        var output = canonicalizeNewlines(writer.toString());
        Assertions.assertEquals("""

Dafny program verifier finished with 5 verified, 0 errors
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
        var reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        reader.transferTo(writer);
        var exitCode = process.waitFor();
        reader.close();
        var output = canonicalizeNewlines(writer.toString());
        Assertions.assertTrue(output.contains("Dafny program verifier finished with 6 verified, 0 errors"));
        Assertions.assertEquals(0, exitCode);

    }

    @Test
    public void testNoConstructor() throws IOException, InterruptedException {
        StringWriter writer = new StringWriter();
        var exitCode = run("NoConstructor.java", false, writer);
        Assertions.assertEquals(0, exitCode);
    }

    record DafnyResults(int successCount, int errorCount) {}
    private void testMarkedSourceFile(String inputFileName, @Nullable DafnyResults dafnyResults) throws IOException {
        var directory = Path.of("./src/test/java/com/aws/jverify");
        var filePath = directory.resolve(inputFileName);
        testMarkedSource(Files.readString(filePath), dafnyResults);
    }
    
    private void testMarkedSource(String markedSource, @Nullable DafnyResults dafnyResults) throws IOException {
        StringWriter writer = new StringWriter();
        var options = getVerifierOptions();
        var result = TestMarkup.getPositionsAndAnnotatedRanges(markedSource);
        var source = result.output();
        var exitCode = Driver.verifyJavaSource(options, source, writer);
        var output = canonicalizeNewlines(writer.toString());
        for(var range : result.ranges()) {
            var positionString = "(" + range.range.toString() + ")";
            String expectation = positionString + ": " + range.annotation;

            assertThat(output, containsString(expectation));
        }
        if (dafnyResults != null) {
            var pluralization = result.ranges().size() > 1 ? "s" : "";
            String ending = "Dafny program verifier finished with " +
                    dafnyResults.successCount() + " verified, " +
                    dafnyResults.errorCount() + " error" + pluralization + "\n";
            assertThat(output, endsWith(ending));
            Assertions.assertEquals(4, exitCode);
        } else {
            Assertions.assertEquals(CommandLine.ExitCode.USAGE, exitCode);
        }
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
                null, null, true,
                new String[] {
                        "--use-basename-for-filename"
                        //,"--wait-for-debugger"
                }
        );
    }
}