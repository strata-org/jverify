package com.aws.jverify.verifier;

import com.aws.jverify.common.AnnotatedRange;
import com.aws.jverify.testengine.JVerifyTestEngine;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.support.hierarchical.OpenTest4JAwareThrowableCollector;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class TestExamples {

    @Test
    public void testUserProfile() throws IOException {
        var markedSourcePath = Path.of("UserProfile.java");
        verifyPath(markedSourcePath, 0, 6, 0, List.of());
    }
    
    @Test
    public void testBinarySearch() throws IOException {
        var markedSourcePath = Path.of("BinarySearch.java");
        verifyPath(markedSourcePath, 0, 3, 0, List.of());
    }
    
    private void verifyPath(Path path, int exitCode, int dafnyVerified, int dafnyErrors, List<AnnotatedRange> ranges) throws IOException {
        var markedSource = Files.readString(Path.of("../examples/src/test/java/com/aws/jverify/examples/").resolve(path));
        JVerifyTestEngine.verifyFile(new SourceFile(path, markedSource),
                new JVerifyTestEngine.TestMetadata(exitCode, dafnyVerified, dafnyErrors), List.of());
    }
}
