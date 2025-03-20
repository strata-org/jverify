package com.aws.jverify;

import com.aws.jverify.verifier.Driver;
import com.aws.jverify.verifier.VerifierOptions;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

public class TestVerifier {
    private static final boolean IS_WINDOWS = System.getProperty("os.name", "").toLowerCase().contains("windows");

    @Test
    public void userProfile() throws IOException {
        StringWriter writer = new StringWriter();
        var exitCode = run("UserProfile.java", writer);
        var output = canonicalizeNewlines(writer.toString());
        Assertions.assertEquals("/test.java(25:12-25:23): Error: a postcondition could not be proved on this return path\n" +
                "/test.java(20:21-20:26): Related location: this is the postcondition that could not be proved\n" +
                "/test.java(22:15-22:76): Related location: this proposition could not be proved\n" +
                "\n" +
                "Dafny program verifier finished with 7 verified, 1 error\n", output);
        Assertions.assertEquals(4, exitCode);
    }
    
    private int run(String inputFileName, Writer writer) throws IOException {
        var source = Files.readString(Path.of("./src/test/java/com/aws/jverify/" + inputFileName));
        var dafnyPath = Path.of("../dafny").toAbsolutePath()
                .resolve(IS_WINDOWS ? "Binaries/Dafny.exe" : "Scripts/dafny");
        var libraryJar = Path.of("../library/build/libs/library.jar");
        var prelude = Path.of("./src/main/resources/additional.dfy");
        var options = new VerifierOptions(dafnyPath, libraryJar, prelude,
                null, null, true,
                new String[] { 
                        //"--wait-for-debugger"
                } 
        );
        return Driver.verifyJavaExample(options, source, writer);
    }
    
    @Test
    public void assertFalse() throws IOException {
        StringWriter writer = new StringWriter();
        var exitCode = run("AssertFalse.java", writer);
        var output = canonicalizeNewlines(writer.toString());
        Assertions.assertEquals("/test.java(7,14): Error: assertion might not hold\n" +
                "\n" +
                "Dafny program verifier finished with 2 verified, 1 error\n", output);
        Assertions.assertEquals(4, exitCode);
    }

    private static void checkErrorAt(String output, int line, int col) {
        Assertions.assertTrue(output.contains("/test.java("+line+","+col+"): Error: assertion might not hold"));
    }

    @Test
    public void operators() throws IOException {
        StringWriter writer = new StringWriter();
        var exitCode = run("Operators.java", writer);
        var output = canonicalizeNewlines(writer.toString());
        Assertions.assertTrue(output.contains("Dafny program verifier finished with 11 verified, 9 errors"));
        // Checking all 9 errors
        checkErrorAt(output,17,14);
        checkErrorAt(output, 31, 14);
        checkErrorAt(output, 45, 14);
        checkErrorAt(output, 59, 14);
        checkErrorAt(output, 71, 14);
        checkErrorAt(output, 83, 14);
        checkErrorAt(output, 95, 14);
        checkErrorAt(output, 107, 14);
        checkErrorAt(output, 119, 14);
        Assertions.assertEquals(4, exitCode);
    }

    @Test
    public void fibonacciValid() throws IOException {
        StringWriter writer = new StringWriter();
        var exitCode = run("FibonacciValid.java", writer);
        var output = canonicalizeNewlines(writer.toString());
        Assertions.assertEquals("\nDafny program verifier finished with 6 verified, 0 errors\n", output);
        Assertions.assertEquals(0, exitCode);

    }

    @Test
    public void fibonacciInvalid() throws IOException {
        StringWriter writer = new StringWriter();
        var exitCode = run("FibonacciInvalid.java", writer);
        var output = canonicalizeNewlines(writer.toString());
        Assertions.assertEquals("/test.java(44,22): Error: value does not satisfy the subset constraints of 'nat32'\n" +
                "/test.java(49,19): Error: value does not satisfy the subset constraints of 'nat32'\n" +
                "/test.java(17,13): Error: a postcondition could not be proved on this return path\n" +
                "/test.java(14,40): Related location: this is the postcondition that could not be proved\n" +
                "/test.java(31,35): Error: value does not satisfy the subset constraints of 'int32'\n" +
                "\n" +
                "Dafny program verifier finished with 4 verified, 4 errors\n", output);
        Assertions.assertEquals(4, exitCode);

    }

    @Test
    public void binarySearchValid() throws IOException {
        StringWriter writer = new StringWriter();
        var exitCode = run("BinarySearchValid.java", writer);
        var output = canonicalizeNewlines(writer.toString());
        Assertions.assertLinesMatch(
                List.of(
                        "Dafny program verifier finished with 5 verified, 0 errors"
                ),
                Arrays.stream(output.split("\n")).toList()
        );
        Assertions.assertEquals(0, exitCode);
    }
    
    @Test
    public void testRunThroughGradle() throws IOException, InterruptedException {
        var gradlePath = IS_WINDOWS ? "../gradlew.bat" : "../gradlew";
        var process = new ProcessBuilder(
                gradlePath,
                ":verifier:run",
                "--args=\"./src/test/java/com/aws/jverify/FibonacciValid.java\"").start();
        var writer = new StringWriter();
        var reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        reader.transferTo(writer);
        var exitCode = process.waitFor();
        reader.close();
        var output = canonicalizeNewlines(writer.toString());
        Assertions.assertTrue(output.contains("Dafny program verifier finished with 6 verified, 0 errors"));
        Assertions.assertEquals(0, exitCode);

    }

    /**
     * Returns the text with all CRLF sequences replaced with LF.
     * This prevents erroneous failures of diff-based assertions on Windows platforms.
     */
    private static String canonicalizeNewlines(final String text) {
        return text.replaceAll("\r\n", "\n");
    }
}