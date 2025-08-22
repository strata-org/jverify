package com.aws.jverify.verifier;

import com.aws.jverify.testengine.JVerifyTestEngine;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class TestExamples {
    
    /**
     * This is the only test that tests these rules.
     */
    @Test
    public void testObjectRules() throws IOException {
        var markedSourcePath = Path.of("ObjectRules.java");
        verifyPath(markedSourcePath, 22, -1, -1, true);
    }
    
    @Test
    public void testImmutableTypes() throws IOException {
        var markedSourcePath = Path.of("ImmutableTypes.java");
        verifyPath(markedSourcePath, 22, -1, -1, true);
    }
    
    @Test
    public void testNullCheck() throws IOException {
        var markedSourcePath = Path.of("NullCheck.java");
        verifyPath(markedSourcePath, 4, 6, 2, false);
    }
    
    @Test
    public void testFibonacci() throws IOException {
        var markedSourcePath = Path.of("Fibonacci.java");
        verifyPath(markedSourcePath, 0, 7, 0, false);
    }
    
    @Test
    public void testUserProfile() throws IOException {
        var markedSourcePath = Path.of("UserProfile.java");
        verifyPath(markedSourcePath, 0, 10, 0, false);
    }
    
    @Test
    public void testBinarySearch() throws IOException {
        var markedSourcePath = Path.of("BinarySearch.java");
        verifyPath(markedSourcePath, 0, 6, 0, false);
    }
    
    private void verifyPath(Path path, int exitCode, int dafnyVerified, int dafnyErrors, boolean continueOnErrors) throws IOException {
        var markedSource = Files.readString(Path.of("../examples/src/test/java/com/aws/jverify/examples/").resolve(path));
        var annotation = JVerifyTestEngine.makeJVerifyTestAnnotation(true, exitCode, dafnyVerified, dafnyErrors, false, continueOnErrors, true);
        JVerifyTestEngine.testMarkedSource(new SourceFile(path, markedSource), annotation);
    }
}
