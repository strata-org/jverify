package com.aws.jverify;

import com.aws.jverify.verifier.Driver;
import com.aws.jverify.verifier.VerifierOptions;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

public class TestVerifier {
    
    private int run(String inputFileName, Writer writer) throws IOException {
        var source = Files.readString(Path.of("./src/test/java/com/aws/jverify/" + inputFileName));
        var dafnyPath = Path.of("../dafny/Scripts/dafny").toAbsolutePath();
        var libraryJar = Path.of("../library/build/libs/library.jar");
        var prelude = Path.of("./src/main/resources/additional.dfy");
        var options = new VerifierOptions(dafnyPath, libraryJar, prelude, null, null, false);
        return Driver.verifyJavaExample(options, source, writer);
    }
    
    @Test
    public void assertFalse() throws IOException {
        StringWriter writer = new StringWriter();
        var exitCode = run("AssertFalse.java", writer);
        var output = writer.toString();
        Assertions.assertEquals("/test.java(7,14): Error: assertion might not hold\n" +
                "\n" +
                "Dafny program verifier finished with 2 verified, 1 error\n", output);
        Assertions.assertEquals(4, exitCode);
    }

    @Test
    public void fibonacciValid() throws IOException {
        StringWriter writer = new StringWriter();
        var exitCode = run("FibonacciValid.java", writer);
        var output = writer.toString();
        Assertions.assertEquals("\nDafny program verifier finished with 6 verified, 0 errors\n", output);
        Assertions.assertEquals(0, exitCode);
        
    }
    @Test
    public void fibonacciInvalid() throws IOException {
        StringWriter writer = new StringWriter();
        var exitCode = run("FibonacciInvalid.java", writer);
        var output = writer.toString();
        Assertions.assertEquals("/test.java(44,22): Error: value does not satisfy the subset constraints of 'nat32'\n" +
                "/test.java(49,19): Error: value does not satisfy the subset constraints of 'nat32'\n" +
                "/test.java(17,13): Error: a postcondition could not be proved on this return path\n" +
                "/test.java(14,34): Related location: this is the postcondition that could not be proved\n" +
                "/test.java(31,35): Error: value does not satisfy the subset constraints of 'int32'\n" +
                "\n" +
                "Dafny program verifier finished with 4 verified, 4 errors\n", output);
        Assertions.assertEquals(4, exitCode);

    }
    
    @Test
    public void testRunThroughGradle() throws IOException, InterruptedException {
        var process = new ProcessBuilder(
                "../gradlew",
                ":verifier:run",
                "--args=\"./src/test/java/com/aws/jverify/FibonacciValid.java\"").start();
        var writer = new StringWriter();
        var reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        reader.transferTo(writer);
        var exitCode = process.waitFor();
        reader.close();
        Assertions.assertTrue(writer.toString().contains("Dafny program verifier finished with 6 verified, 0 errors"));
        Assertions.assertEquals(0, exitCode);

    }
}