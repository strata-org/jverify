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
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        VerifierOptions options = JVerifyTestEngine.getVerifierOptions(annotation, 
                new PositionFilter(false, "MultiPackageTest.java", filterLine, filterLine));
        JVerifyTestEngine.verifyFile(markedSourceFile, annotation, remainingRanges, options);
    }

    @Test
    public void verifyFibonacciTrackTime() {
        var dafnyPath = JVerifyTestEngine.getDafnyInSubmodulePath();

        var command = new CommandLine(new AppCommand());
        StringWriter out = new StringWriter();
        command.setOut(new PrintWriter(out));
        command.setErr(new PrintWriter(out));

        var exitCode = command.execute(
                Path.of("../examples/src/test/java/com/aws/jverify/examples/Fibonacci.java").toString(),
                "--track-time",
                "--dafny=" + dafnyPath);
        String output = out.getBuffer().toString();
        assertTrue(output.contains("Calling Driver.verifyJavaPaths took"));
        assertTrue(output.contains("Compiling Java to Dafny took"));
        assertTrue(output.contains("Serializing Dafny AST took"));
        assertTrue(output.contains("Running Dafny took"));
        assertTrue(output.contains("verifyAll took"));
        
        Assertions.assertEquals(0, exitCode, output);
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
    public void testIncludeDependencies() {
        var dafnyPath = JVerifyTestEngine.getDafnyInSubmodulePath();

        var path = Path.of("./src/test/java/com/aws/jverify/verifier/tests/javasupport/packages");
        var main = path.resolve(Path.of("MultiPackageTest.java"));
        var a = path.resolve(Path.of("a/Foo.java"));
        var b = path.resolve(Path.of("b/Foo.java"));
        var c = path.resolve(Path.of("c/Foo.java"));
        var command = new CommandLine(new AppCommand());
        StringWriter withoutDependenciesOutput = new StringWriter();
        command.setOut(new PrintWriter(withoutDependenciesOutput));
        command.setErr(new PrintWriter(withoutDependenciesOutput));

        var testEngineClassPath = Path.of("../test-engine/build/classes/java/main").toAbsolutePath();
        var exitCode1 = command.execute(
                main.toString(), a.toString(), b.toString(), c.toString(),
                "--filter-position=MultiPackageTest.java",
                "--builtin-contracts=false",
                "--jar=" + testEngineClassPath,
                "--dafny=" + dafnyPath);

        assertTrue(withoutDependenciesOutput.toString().contains("2 errors"), withoutDependenciesOutput.toString());
        StringWriter withDependenciesOutput = new StringWriter();
        command.setOut(new PrintWriter(withDependenciesOutput));
        command.setErr(new PrintWriter(withDependenciesOutput));
        var exitCode2 = command.execute(
                main.toString(), a.toString(), b.toString(), c.toString(),
                "--filter-position=MultiPackageTest.java",
                "--include-filter-dependencies",
                "--builtin-contracts=false",
                "--jar=" + testEngineClassPath,
                "--dafny=" + dafnyPath);
        assertTrue(withDependenciesOutput.toString().contains("3 errors"));
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
        assertTrue(out.toString().startsWith("src/test/resources/AssertFalse.java"), out.toString());
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

    /**
     * Returns the text with all CRLF sequences replaced with LF.
     * This prevents erroneous failures of diff-based assertions on Windows platforms.
     */
    private static String canonicalizeNewlines(final String text) {
        return text.replaceAll("\r\n", "\n");
    }
}