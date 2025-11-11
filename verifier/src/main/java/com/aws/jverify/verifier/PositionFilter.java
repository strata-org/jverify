package com.aws.jverify.verifier;

import com.sun.tools.javac.tree.JCTree;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.nio.file.Path;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record PositionFilter(@Nullable String fileEnding, @Nullable Integer start, @Nullable Integer end) {

    public boolean unitPasses(VerifierOptions options,  JCTree.JCCompilationUnit compilationUnit) {
        var resolved = options.workingDirectory().resolve(compilationUnit.getSourceFile().getName());
        return fileEnding == null || resolved.endsWith(fileEnding());
    }
    
    public static @Nullable PositionFilter getPositionFilter(String filterPosition) {
        if (filterPosition == null) {
            return null;
        }
        Pattern p = Pattern.compile("(.*)(?::(\\d*)(-?)(\\d*))?$");
        Matcher matcher = p.matcher(filterPosition);

        if (!matcher.find()) {
            throw new RuntimeException("");
        }

        var filePart = matcher.group(1);
        String lineStart = matcher.group(2);
        boolean hasRange = Objects.equals(matcher.group(3), "-");
        String lineEnd = matcher.group(3);

        Integer start = null;
        Integer end = null;
        if (lineStart != null && !lineStart.isEmpty()) {
            start = Integer.parseInt(lineStart);
        }
        if (lineEnd != null && !lineEnd.isEmpty()) {
            end = Integer.parseInt(lineEnd);
        }
        return new PositionFilter(filePart, start ,end);
    }
}
