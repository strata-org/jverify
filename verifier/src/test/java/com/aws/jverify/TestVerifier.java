package com.aws.jverify;

import com.aws.jverify.common.Position;
import com.aws.jverify.common.TestMarkup;
import com.aws.jverify.verifier.Driver;
import com.aws.jverify.verifier.VerifierOptions;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

public class TestVerifier {
    private static final boolean IS_WINDOWS = System.getProperty("os.name", "").toLowerCase().contains("windows");

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
    public void assertFalse() throws IOException {
        testMarkedSourceFile("AssertFalse.java", 2, 1);
    }

    @Test
    public void fibonacciInvalid() throws IOException {
        testMarkedSourceFile("FibonacciInvalid.java", 4, 4);
    }

    @Test
    public void operators() throws IOException {
        StringWriter writer = new StringWriter();
        var exitCode = run("Operators.java", false, writer);
        var output = canonicalizeNewlines(writer.toString());
        Assertions.assertTrue(output.contains("Dafny program verifier finished with 11 verified, 9 errors"));
        // Checking all 9 errors
        checkErrorAt(output,17,9);
        checkErrorAt(output, 31, 9);
        checkErrorAt(output, 45, 9);
        checkErrorAt(output, 59, 9);
        checkErrorAt(output, 71, 9);
        checkErrorAt(output, 83, 9);
        checkErrorAt(output, 95, 9);
        checkErrorAt(output, 107, 9);
        checkErrorAt(output, 119, 9);
        Assertions.assertEquals(4, exitCode);
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

    private void testMarkedSourceFile(String inputFileName, int successCount, int errorCount) throws IOException {
        var directory = Path.of("./src/test/java/com/aws/jverify");
        var filePath = directory.resolve(inputFileName);
        testMarkedSource(Files.readString(filePath), successCount, errorCount);
    }
    
    private void testMarkedSource(String markedSource, int successCount, int errorCount) throws IOException {
        StringWriter writer = new StringWriter();
        var options = getVerifierOptions();
        var result = TestMarkup.getPositionsAndAnnotatedRanges(markedSource);
        var source = result.output();
        var exitCode = Driver.verifyJavaSource(options, source, writer);
        var output = canonicalizeNewlines(writer.toString());
        for(var range : result.ranges()) {
            var positionString = "(" + rangeToString(range.range) + ")";
            String expectation = positionString + ": " + range.annotation;
            
            Assertions.assertTrue(output.contains(expectation));
        }
        var pluralization = result.ranges().size() > 1 ? "s" : "";
        Assertions.assertTrue(output.endsWith("Dafny program verifier finished with " + successCount + " verified, " + errorCount + " error" + pluralization + "\n"));
        Assertions.assertEquals(4, exitCode);
    }

    private static void checkErrorAt(String output, int line, int col) {
        Assertions.assertTrue(output.contains("Operators.java(" + line + ":" + col + "-"));
    }
    
    /**
     * Returns the text with all CRLF sequences replaced with LF.
     * This prevents erroneous failures of diff-based assertions on Windows platforms.
     */
    private static String canonicalizeNewlines(final String text) {
        return text.replaceAll("\r\n", "\n");
    }

    String rangeToString(TestMarkup.Range range) {
        return positionToString(range.start) + "-" + positionToString(range.end);
    }

    String positionToString(Position position) {
        return (position.line + 1) + ":" + (position.character + 1);
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
                        //"--wait-for-debugger"
                }
        );
    }
}