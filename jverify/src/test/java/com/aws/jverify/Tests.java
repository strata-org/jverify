package com.aws.jverify;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

public class Tests {
    
    @Test
    public void assertFalse() throws IOException {
        var source = Files.readString(Path.of("/Users/rwillems/SourceCode/GradleBased/jverify/src/test/java/com/aws/jverify/AssertFalse.java"));
        StringWriter writer = new StringWriter();
        var exitCode = Driver.verifyJavaExample(source, writer);
        var output = writer.toString();
        Assertions.assertEquals(4, exitCode);
        Assertions.assertEquals("<stdin>(7,14): Error: assertion might not hold\n" +
                "\n" +
                "Dafny program verifier finished with 0 verified, 1 error\n", output);
    }

    @Test
    public void fibonacci() throws IOException {
        var source = Files.readString(Path.of("/Users/rwillems/SourceCode/GradleBased/jverify/src/test/java/com/aws/jverify/Fibonacci.java"));
        StringWriter writer = new StringWriter();
        var exitCode = Driver.verifyJavaExample(source, writer);
        var output = writer.toString();
        Assertions.assertEquals("/test.java(19,13): Error: a postcondition could not be proved on this return path\n" +
                "/test.java(15,34): Related location: this is the postcondition that could not be proved\n" +
                "\n" +
                "Dafny program verifier finished with 3 verified, 1 error\n", output);
        Assertions.assertEquals(4, exitCode);
        
    }
}