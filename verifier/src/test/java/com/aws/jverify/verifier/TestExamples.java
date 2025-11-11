package com.aws.jverify.verifier;

import com.aws.jverify.testengine.JVerifyTestEngine;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class TestExamples {
    
    @Test
    public void testSumCache() throws IOException {
        var markedSourcePath = Path.of("SumCache.java");
        verifyPath(markedSourcePath, 4, 7, 1, false, true);
    }
    
    /**
     * This is the only test that tests these rules.
     */
    @Test
    public void testObjectRules() throws IOException {
        var markedSourcePath = Path.of("ObjectRules.java");
        verifyPath(markedSourcePath, 22, -1, -1, true, true);
    }
    
    @Test
    public void testPurity() throws IOException {
        var markedSourcePath = Path.of("TypePurity.java");
        verifyPath(markedSourcePath, 22, -1, -1, true, true);
    }
    
    @Test
    public void testNullCheck() throws IOException {
        var markedSourcePath = Path.of("SimpleNullCheck.java");
        verifyPath(markedSourcePath, 4, 5, 2, false, true);
    }
    
    @Test
    public void testFibonacci() throws IOException {
        var markedSourcePath = Path.of("Fibonacci.java");
        verifyPath(markedSourcePath, 0, 6, 0, false, true);
    }

    @Test
    public void testOrder() throws IOException {
        var markedSourcePath = Path.of("Order.java");
        verifyPath(markedSourcePath, 4, 5, 1, false, true);
    }
    
    @Test
    public void testUserProfile() throws IOException {
        var markedSourcePath = Path.of("UserProfile.java");
        verifyPath(markedSourcePath, 0, 9, 0, false, true);
    }
    
    @Test
    public void testBinarySearch() throws IOException {
        var markedSourcePath = Path.of("BinarySearch.java");
        verifyPath(markedSourcePath, 0, 5, 0, false, true);
    }
    
    private void verifyPath(Path path, int exitCode, int dafnyVerified, int dafnyErrors, 
                            boolean continueOnErrors, 
                            boolean useBuiltinContracts) throws IOException {
        var markedSource = Files.readString(Path.of("../examples/src/test/java/com/aws/jverify/examples/").resolve(path));
        var annotation = JVerifyTestEngine.makeJVerifyTestAnnotation(true, exitCode, dafnyVerified, dafnyErrors, 
                false, continueOnErrors, useBuiltinContracts);
        JVerifyTestEngine.testMarkedSource(new SourceFile(path, markedSource), annotation);
    }
}
