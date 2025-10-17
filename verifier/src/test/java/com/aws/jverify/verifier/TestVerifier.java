package com.aws.jverify.verifier;

import com.aws.jverify.common.AnnotatedRange;
import com.aws.jverify.common.Common;
import com.aws.jverify.testengine.JVerifyTest;
import com.aws.jverify.testengine.JVerifyTestEngine;
import com.aws.jverify.testengine.JVerifyTestRecord;
import com.aws.jverify.testengine.TestMarkup;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.*;
import java.lang.annotation.Annotation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static com.aws.jverify.testengine.JVerifyTestEngine.testMarkedSource;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;

public class TestVerifier {
    private static final boolean IS_WINDOWS = System.getProperty("os.name", "").toLowerCase().contains("windows");

    @Test
    public void testFilterPosition() throws IOException {
        var markedSourcePath = Path.of("./src/test/java/com/aws/jverify/verifier/tests/javasupport/packages/MultiPackageTest.java");
        var markedSource = Files.readString(markedSourcePath);
        var annotation = new JVerifyTestRecord("", true, true, false, 
                4, 7, 1, new String[] { "./a/Foo.java", "./b/Foo.java" }, false,
                -1, -1, -1);
        SourceFile markedSourceFile = new SourceFile(markedSourcePath, markedSource);
        var parsedMarkup = TestMarkup.getPositionsAndAnnotatedRanges(markedSourceFile.getCharContent(false));
        List<AnnotatedRange> ranges = parsedMarkup.ranges();
        var remainingRanges = List.of(ranges.get(0), ranges.get(2));
        int filterLine = remainingRanges.get(1).range().start().line() - 3;
        VerifierOptions options = JVerifyTestEngine.getVerifierOptions(annotation, new PositionFilter("MultiPackageTest.java", filterLine, filterLine));
        JVerifyTestEngine.verifyFile(markedSourceFile, annotation, remainingRanges, options);
    }
    
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
    public void verifyBinarySearch() {
        var dafnyPath = JVerifyTestEngine.getDafnyInSubmodulePath();

        var command = new CommandLine(new AppCommand());
        StringWriter out = new StringWriter();
        command.setOut(new PrintWriter(out));
        command.setErr(new PrintWriter(out));

        var exitCode = command.execute(
                Path.of("../examples/src/test/java/com/aws/jverify/examples/BinarySearch.java").toString(),
                "--dafny=" + dafnyPath,
                "--print-dafny=../build/temp.dfy");
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
        Assertions.assertTrue(out.toString().startsWith("src/test/resources/AssertFalse.java"), out.toString());
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
                "--print-dafny=../build/temp.dfy",
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
        assertThat(output, containsString("Dafny program verifier finished with 6 verified, 0 errors"));
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