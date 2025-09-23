package com.aws.jverify.verifier;

import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record PositionFilter(@Nullable String fileEnding, int start, int end) {

    public static PositionFilter getPositionFilter(String filterPosition) {
        Pattern p = Pattern.compile("(.*)(?::(\\d*)(-?)(\\d*))?$");
        Matcher matcher = p.matcher(filterPosition);

        if (!matcher.find()) {
            throw new RuntimeException("");
        }

        var filePart = matcher.group(1);
        String lineStart = matcher.group(2);
        boolean hasRange = Objects.equals(matcher.group(3), "-");
        String lineEnd = matcher.group(3);

        int start = Integer.MIN_VALUE;
        int end = Integer.MAX_VALUE;
        if (lineEnd != null && !lineEnd.isEmpty()) {
            end = Integer.parseInt(lineEnd);
            if (!hasRange) {
                start = end;
            }
        }
        if (lineStart != null && !lineStart.isEmpty()) {
            start = Integer.parseInt(lineStart);
        }
        return new PositionFilter(filePart, start ,end);
    }
}
