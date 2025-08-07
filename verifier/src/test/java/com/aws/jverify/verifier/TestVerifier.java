package com.aws.jverify.verifier;

import com.aws.jverify.common.Common;
import com.aws.jverify.testengine.JVerifyTestEngine;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.*;
import java.nio.file.Path;

import static com.aws.jverify.testengine.JVerifyTestEngine.testMarkedSource;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;

public class TestVerifier {
    private static final boolean IS_WINDOWS = System.getProperty("os.name", "").toLowerCase().contains("windows");

    @Test
    public void verifyFibonacci() {
        var dafnyPath = JVerifyTestEngine.getDafnyInSubmodulePath();
        
        var command = new CommandLine(new AppCommand());
        StringWriter out = new StringWriter();
        command.setOut(new PrintWriter(out));
        command.setErr(new PrintWriter(out));

        var exitCode = command.execute(
                Path.of("../examples/src/test/java/com/aws/jverify/examples/Fibonacci.java").toString(),
                "--dafny=" + dafnyPath);
        Assertions.assertEquals(0, exitCode, out.getBuffer().toString());
    }

    @Test
    public void checkPathsOutput() {
        var dafnyPath = JVerifyTestEngine.getDafnyInSubmodulePath();

        var command = new CommandLine(new AppCommand());
        StringWriter out = new StringWriter();
        command.setOut(new PrintWriter(out));
        command.setErr(new PrintWriter(out));

        var exitCode = command.execute(
                Path.of("./src/test/resources/AssertFalse.java").toString(),
                "--dafny=" + dafnyPath, "--paths");
        Assertions.assertTrue(out.toString().startsWith("src/test/resources/AssertFalse.java"));
        Assertions.assertEquals(4, exitCode);
    }

    @Test
    public void verifyBuiltinContracts() {
        var dafnyPath = JVerifyTestEngine.getDafnyInSubmodulePath();

        var command = new CommandLine(new AppCommand());
        StringWriter out = new StringWriter();
        command.setOut(new PrintWriter(out));
        command.setErr(new PrintWriter(out));

        var exitCode = command.execute(
                Path.of("./src/main/resources/builtin-contracts.java").toString(),
                "--dafny=" + dafnyPath,
                "--builtin-contracts=false");
        Assertions.assertEquals(0, exitCode, out.toString());
    }
    
    @Test
    public void javaError() throws IOException {
        var source = Common.getResourceFile(getClass(), "/JavaError.java");
        var annotation = JVerifyTestEngine.makeJVerifyTestAnnotation(true, 2, -1, -1, false, false, true);
        testMarkedSource(new SourceFile("JavaError.java", source), annotation);
    }

    @Test
    public void testRunThroughGradle() throws IOException, InterruptedException {
        var gradlePath = IS_WINDOWS ? "../gradlew.bat" : "../gradlew";
        ProcessBuilder processBuilder = new ProcessBuilder(
                gradlePath,
                ":verifier:run",
                "--args=\"../examples/src/test/java/com/aws/jverify/examples/Fibonacci.java\"");
        processBuilder.redirectErrorStream(true);
        var process = processBuilder.start();
        var writer = new StringWriter();
        int exitCode;
        try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            reader.transferTo(writer);
            exitCode = process.waitFor();
        }
        var output = canonicalizeNewlines(writer.toString());
        assertThat(output, containsString("Dafny program verifier finished with 7 verified, 0 errors"));
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