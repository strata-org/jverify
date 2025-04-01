// Modified after being taken from https://github.com/dotnet/roslyn/blob/576abfa9a8f2d64967ceabe7fa0e5b7ebe550752/src/Compilers/Test/Core/MarkedSource/MarkupTestFile.cs
// Licensed to the .NET Foundation under one or more agreements.
// The .NET Foundation licenses this file to you under the MIT license.
// See the LICENSE file in the project root for more information.

package com.aws.jverify.common;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * To aid with testing, we define a special type of text file that can encode additional
 * information in it.  This prevents a test writer from having to carry around multiple sources
 * of information that must be reconstituted.  For example, instead of having to keep around the
 * contents of a file *and* and the location of the cursor, the tester can just provide a
 * string with the "><" string in it.  This allows for easy creation of "FIT" tests where all
 * that needs to be provided are strings that encode every bit of state necessary in the string
 * itself.
 *
 * The current set of encoded features we support are: 
 *
 * >< - The position in the file.  There can be zero or more of these.
 *
 * [> ... <] - A span of text in the file.  There can be many of these and they can be nested
 * and/or overlap the >< position(s).
 *
 * {>Name: ... <} A span of text in the file annotated with an identifier.  There can be many of
 * these, including ones with the same name.
 *
 * (>metadata::: ... <) A span of text in the file annotated with metdata.
 *
 * Additional encoded features can be added on a case by case basis.
 */
public class TestMarkup {
    private static final String SPAN_START_STRING = "[>";
    private static final String SPAN_END_STRING = "<]";

    private static String parse(String input, List<Integer> positions, List<AnnotatedSpan> spans) {
        StringBuilder outputBuilder = new StringBuilder();

        // A stack of span starts along with their associated annotation name.  [><] spans simply
        // have empty string for their annotation name.
        Stack<Pair<Integer, String>> spanStartStack = new Stack<>();
        Stack<Pair<Integer, String>> namedSpanStartStack = new Stack<>();
        Stack<Pair<Integer, String>> annotatedSpanStartStack = new Stack<>();

        Pattern r = Pattern.compile("(?<Position>><)|" +
                "(?<SpanStart>\\[>)|(?<SpanEnd><\\])" +
                "|(?<NameSpanStart>\\{>(?<Name>[-_.'=(){}\"A-Za-z0-9\\*\\+]+)\\:)|(?<NameSpanEnd><\\})" +
                "|(?<AnnotatedSpanStart>\\(>(?<Annotation>(.|\\n)+)\\:\\:\\:)|(?<AnnotatedSpanEnd><\\))" +
                "|(\\(>(?<StandaloneAnnotation>([.|\\n])+)<\\))");

        int outputIndex = 0;
        int inputIndex = 0;
        Matcher matcher = r.matcher(input);

        while (matcher.find()) {
            int diff = inputIndex - outputIndex;
            int matchIndexInOutput = matcher.start() - diff;
            String outputPart = input.substring(inputIndex, matcher.start());
            outputIndex += outputPart.length();
            outputBuilder.append(outputPart);
            inputIndex = matcher.end();

            if (matcher.group("Position") != null) {
                positions.add(matchIndexInOutput);
            } else if (matcher.group("SpanStart") != null) {
                spanStartStack.push(new Pair<>(matchIndexInOutput, ""));
            } else if (matcher.group("NameSpanStart") != null) {
                namedSpanStartStack.push(new Pair<>(matchIndexInOutput, matcher.group("Name")));
            } else if (matcher.group("AnnotatedSpanStart") != null) {
                annotatedSpanStartStack.push(new Pair<>(matchIndexInOutput, matcher.group("Annotation")));
            } else if (matcher.group("StandaloneAnnotation") != null) {
                annotatedSpanStartStack.push(new Pair<>(matchIndexInOutput, matcher.group("StandaloneAnnotation")));
                popSpan(annotatedSpanStartStack, spans, matchIndexInOutput);
            } else if (matcher.group("SpanEnd") != null) {
                popSpan(spanStartStack, spans, matchIndexInOutput);
            } else if (matcher.group("NameSpanEnd") != null) {
                popSpan(namedSpanStartStack, spans, matchIndexInOutput);
            } else if (matcher.group("AnnotatedSpanEnd") != null) {
                popSpan(annotatedSpanStartStack, spans, matchIndexInOutput);
            }
        }

        if (!spanStartStack.isEmpty()) {
            throw new IllegalArgumentException("Saw " + SPAN_START_STRING + " without matching " + SPAN_END_STRING);
        }

        if (!namedSpanStartStack.isEmpty()) {
            throw new IllegalArgumentException("Saw '{<' without matching '>}'");
        }

        
        // Append the remainder of the string.
        outputBuilder.append(input.substring(inputIndex));
        String output = outputBuilder.toString();

        var hatAnnotations = findHatAnnotations(output);
        spans.addAll(hatAnnotations);
        
        return output;
    }
    /**
     * Finds hat-style annotations in the form:
     * code line
     * //. ^^^^^ annotation text
     * where the carets point to the relevant code in the line above
     */
    private static List<AnnotatedSpan> findHatAnnotations(String text) {
        List<AnnotatedSpan> result = new ArrayList<>();
        String[] lines = text.split("\\r?\\n");

        // Track cumulativePosition to convert line/col coordinates to absolute position
        int cumulativePosition = 0;

        for (int i = 0; i < lines.length - 1; i++) {
            String currentLine = lines[i];
            String nextLine = lines[i + 1];

            // Look for hat annotation pattern in the next line
            Pattern pattern = Pattern.compile("^//\\s+(\\^+)\\s+(.+)$");
            Matcher matcher = pattern.matcher(nextLine);

            if (matcher.find()) {
                String carets = matcher.group(1);
                String annotation = matcher.group(2).trim();

                // Find where in the next line the carets start
                int caretStartIndex = nextLine.indexOf(carets);

                // Check if caret position is within current line bounds 
                if (caretStartIndex >= 0 && caretStartIndex < currentLine.length()) {
                    // Calculate absolute position for the start of the annotated range
                    int spanStart = cumulativePosition + caretStartIndex;
                    int spanLength = carets.length();

                    // Ensure the length doesn't exceed the current line
                    spanLength = Math.min(spanLength, currentLine.length() - caretStartIndex);

                    // Create a span for the annotated region
                    TextSpan span = new TextSpan(spanStart, spanLength);
                    result.add(new AnnotatedSpan(annotation, span));
                }
            }

            // Update cumulative position for next iteration
            cumulativePosition += currentLine.length() + 1; // +1 for newline
        }

        return result;
    }

    private static void popSpan(
            Stack<Pair<Integer, String>> spanStartStack,
            List<AnnotatedSpan> spans,
            int finalIndex) {
        Pair<Integer, String> pair = spanStartStack.pop();
        int matchIndex = pair.first;
        String name = pair.second;

        TextSpan span = new TextSpan(matchIndex, finalIndex - matchIndex);
        spans.add(new AnnotatedSpan(name, span));
    }
    
    public static String getIndexAndSpans(
            String input, List<Integer> positions, List<AnnotatedSpan> spans) {
        return parse(input, positions, spans);
    }

    public record GetPositionsAndNamedRangesResult(String output, List<Position> positions, Map<String, List<Range>> namedRanges) {}
    public static GetPositionsAndNamedRangesResult getPositionsAndNamedRanges(String input) {
        Map<String, List<Range>> namedRanges = new HashMap<>();
        
        var output = getPositionsAndAnnotatedRanges(input);
        for (AnnotatedRange annotatedRange : output.ranges) {
            namedRanges.computeIfAbsent(annotatedRange.annotation, k -> new ArrayList<>())
                    .add(annotatedRange.range);
        }
        
        return new GetPositionsAndNamedRangesResult(output.output, output.positions, namedRanges);
    }

    public record GetPositionsAndAnnotatedRangesResult(String output, List<Position> positions, List<AnnotatedRange> ranges) {}
    public static GetPositionsAndAnnotatedRangesResult getPositionsAndAnnotatedRanges(String input) {
        List<Integer> positionIndices = new ArrayList<>();
        List<AnnotatedSpan> spans = new ArrayList<>();
        List<Position> positions = new ArrayList<>();
        List<AnnotatedRange> ranges = new ArrayList<>();
        
        var output = getIndexAndSpans(input, positionIndices, spans);

        TextBuffer buffer = new TextBuffer(output);

        for (Integer index : positionIndices) {
            positions.add(buffer.fromIndex(index));
        }

        for (AnnotatedSpan span : spans) {
            Range range = new Range(
                    buffer.fromIndex(span.span.start),
                    buffer.fromIndex(span.span.end())
            );
            ranges.add(new AnnotatedRange(span.annotation, range));
        }
        return new GetPositionsAndAnnotatedRangesResult(output, positions, ranges);
    }

    public static String getPositionsAndRanges(String input, List<Position> positions, List<Range> ranges) {
        List<Integer> positionIndices = new ArrayList<>();
        List<AnnotatedSpan> spans = new ArrayList<>();
        var output = getIndexAndSpans(input, positionIndices, spans);

        TextBuffer buffer = new TextBuffer(output);

        for (Integer index : positionIndices) {
            positions.add(buffer.fromIndex(index));
        }

        for (AnnotatedSpan span : spans) {
            ranges.add(new Range(
                    buffer.fromIndex(span.span.start),
                    buffer.fromIndex(span.span.end())
            ));
        }
        
        return output;
    }

    public record GetPositionAndRangesResult(String output, Position position) {}
    public static GetPositionAndRangesResult getPositionAndRanges(String input, List<Range> ranges) {
        List<Position> positions = new ArrayList<>();
        var output = getPositionsAndRanges(input, positions, ranges);

        if (positions.size() != 1) {
            throw new IllegalArgumentException("Expected exactly one position in the markup");
        }
        return new GetPositionAndRangesResult(output, positions.getFirst());
    }
}


// Supporting classes needed for the translation
class TextSpan {
    public final int start;
    public final int length;

    public TextSpan(int start, int length) {
        this.start = start;
        this.length = length;
    }

    public int end() {
        return start + length;
    }
}

class TextBuffer {
    private final String text;
    private final int[] lineStarts;

    public TextBuffer(String text) {
        this.text = text;
        lineStarts = computeLineStarts(text);
    }

    private int[] computeLineStarts(String text) {
        List<Integer> starts = new ArrayList<>();
        starts.add(0); // First line starts at index 0

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n') {
                starts.add(i + 1);
            } else if (c == '\r') {
                // Check for Windows-style \r\n
                if (i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    // Skip the next character (\n) since we're handling the pair
                    i++;
                }
                starts.add(i + 1);
            }
        }

        return starts.stream().mapToInt(Integer::intValue).toArray();
    }

    public Position fromIndex(int index) {
        // Binary search to find the line
        int line = Arrays.binarySearch(lineStarts, index);
        if (line < 0) {
            // If not exact match, binarySearch returns (-(insertion point) - 1)
            line = -line - 2;
        }

        int character = index - lineStarts[line];
        return new Position(line, character);
    }
}

class Pair<T, U> {
    public final T first;
    public final U second;

    public Pair(T first, U second) {
        this.first = first;
        this.second = second;
    }
}