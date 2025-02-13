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
}