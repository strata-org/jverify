package com.aws.jverify.verifier;

import com.aws.jverify.common.AnnotatedRange;
import com.aws.jverify.common.Common;
import com.aws.jverify.testengine.JVerifyTestEngine;
import com.aws.jverify.testengine.TestMarkup;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class TestUserProfileEdits {
    
    @Test
    public void edits() throws IOException {
        var source = Common.getResourceFile(getClass(), "/AnnotatedUserProfile.java");
        var outputParts = TestMarkup.parseParts(source, new ArrayList<>(), new ArrayList<>());
        
        var first = keepAnnotations(outputParts);

        var path = Path.of("/AnnotatedUserProfile.java"); 
        var firstTestAnnotation = JVerifyTestEngine.makeJVerifyTestAnnotation(7, 1);
        JVerifyTestEngine.verifyFile(new SourceFile(path, first), firstTestAnnotation, List.of(TestMarkup.findHatAnnotationRanges(first).get(0)));
        
        var second = keepAnnotations(outputParts, 0);
        var secondTestAnnotation = JVerifyTestEngine.makeJVerifyTestAnnotation(8, 1);
        JVerifyTestEngine.verifyFile(new SourceFile(path, second), secondTestAnnotation, List.of(TestMarkup.findHatAnnotationRanges(second).get(2)));
        
        var third = keepAnnotations(outputParts, 0, 1);
        var thirdTestAnnotation = JVerifyTestEngine.makeJVerifyTestAnnotation(9, 1);
        List<AnnotatedRange> hatAnnotationRanges = TestMarkup.findHatAnnotationRanges(third);
        JVerifyTestEngine.verifyFile(new SourceFile(path, third), thirdTestAnnotation, List.of(hatAnnotationRanges.get(2), hatAnnotationRanges.get(1), hatAnnotationRanges.get(3)));
        
        var fourth = keepAnnotations(outputParts, 0, 1, 2);
        var fourthTestAnnotation = JVerifyTestEngine.makeJVerifyTestAnnotation(10, 0);
        JVerifyTestEngine.verifyFile(new SourceFile(path, fourth), fourthTestAnnotation, List.of());
    }
    
    private String keepAnnotations(List<String> parts, int... indices) {
        var indicesToKeep = Arrays.stream(indices).boxed().map(i -> i * 2 + 1).collect(Collectors.toSet());
        
        var outputParts = new ArrayList<String>();
        for(var index = 0; index < parts.size(); index++) {
            if (index % 2 == 0 || indicesToKeep.contains(index)) {
                outputParts.add(parts.get(index));
            }
        }
        return String.join("", outputParts);
    }
}
