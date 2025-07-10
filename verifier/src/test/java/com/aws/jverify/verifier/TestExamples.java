package com.aws.jverify.verifier;

import com.aws.jverify.testengine.JVerifyTestEngine;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class TestExamples {
    @Test
    public void testFibonacci() throws IOException {
        var markedSourcePath = Path.of("Fibonacci.java");
        verifyPath(markedSourcePath, 0, 4, 0);
    }

    
    @Test
    public void testUserProfile() throws IOException {
        var markedSourcePath = Path.of("UserProfile.java");
        verifyPath(markedSourcePath, 0, 6, 0);
    }
    
    @Test
    public void testBinarySearch() throws IOException {
        var markedSourcePath = Path.of("BinarySearch.java");
        verifyPath(markedSourcePath, 0, 3, 0);
    }
    
    private void verifyPath(Path path, int exitCode, int dafnyVerified, int dafnyErrors) throws IOException {
        var markedSource = Files.readString(Path.of("../examples/src/test/java/com/aws/jverify/examples/").resolve(path));
        var annotation = JVerifyTestEngine.makeJVerifyTestAnnotation(true, exitCode, dafnyVerified, dafnyErrors, false, false, true);
        JVerifyTestEngine.testMarkedSource(new SourceFile(path, markedSource), annotation);
    }
}
