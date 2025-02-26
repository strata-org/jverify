package com.aws.jverify;

import com.aws.jverify.verifier.Driver;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

public class TestVerifier {
    
    @Test
    public void assertFalse() throws IOException {
        var source = Files.readString(Path.of("/Users/rwillems/SourceCode/GradleBased/jverify/src/test/java/com/aws/jverify/AssertFalse.java"));
        StringWriter writer = new StringWriter();
        var exitCode = Driver.verifyJavaExample(source, writer);
        var output = writer.toString();
        Assertions.assertEquals(4, exitCode);
        Assertions.assertEquals("/test.java(7,14): Error: assertion might not hold\n" +
                "\n" +
                "Dafny program verifier finished with 2 verified, 1 error\n", output);
    }

    @Test
    public void fibonacciValid() throws IOException {
        var source = Files.readString(Path.of("/Users/rwillems/SourceCode/GradleBased/jverify/src/test/java/com/aws/jverify/FibonacciValid.java"));
        StringWriter writer = new StringWriter();
        var exitCode = Driver.verifyJavaExample(source, writer);
        var output = writer.toString();
        Assertions.assertEquals("\nDafny program verifier finished with 6 verified, 0 errors\n", output);
        Assertions.assertEquals(0, exitCode);
        
    }
    @Test
    public void fibonacciInvalid() throws IOException {
        var source = Files.readString(Path.of("/Users/rwillems/SourceCode/GradleBased/jverify/src/test/java/com/aws/jverify/FibonacciInvalid.java"));
        StringWriter writer = new StringWriter();
        var exitCode = Driver.verifyJavaExample(source, writer);
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
}