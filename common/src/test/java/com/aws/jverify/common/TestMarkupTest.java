package com.aws.jverify.common;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class TestMarkupTest {

    @Test
    public void testPositionMarkers() {
        String source = "int ><x = ><10;";

        List<Position> positions = new ArrayList<>();
        List<Range> ranges = new ArrayList<>();

        var output = TestMarkup.getPositionsAndRanges(source, positions, ranges);

        assertEquals("int x = 10;", output);
        assertEquals(2, positions.size());

        assertEquals(0, positions.get(0).line());
        assertEquals(4, positions.get(0).character());

        assertEquals(0, positions.get(1).line());
        assertEquals(8, positions.get(1).character());
    }

    @Test
    public void testBasicSpan() {
        String source = "int x = [>10<];";

        List<Position> positions = new ArrayList<>();
        List<Range> ranges = new ArrayList<>();

        var output = TestMarkup.getPositionsAndRanges(source, positions, ranges);

        assertEquals("int x = 10;", output);
        assertEquals(0, positions.size());
        assertEquals(1, ranges.size());

        var span = ranges.getFirst();
        assertEquals(0, span.start().line());
        assertEquals(8, span.start().character());
        assertEquals(0, span.end().line());
        assertEquals(10, span.end().character());
    }

    @Test
    public void testNamedSpans() {
        String source = "int x = {>Value:10<};";

        var result = TestMarkup.getPositionsAndAnnotatedRanges(source);

        assertEquals("int x = 10;", result.output());
        assertEquals(1, result.ranges().size());

        var span = result.ranges().getFirst();
        assertEquals("Value", span.annotation);
        assertEquals(0, span.range.start().line());
        assertEquals(8, span.range.start().character());
        assertEquals(0, span.range.end().line());
        assertEquals(10, span.range.end().character());
    }

    @Test
    public void testHatAnnotations() {
        String source = """
   int x = 10;
// ^^^^^ Variable declaration
""";
        var result = TestMarkup.getPositionsAndAnnotatedRanges(source);

        assertEquals(1, result.ranges().size());

        var span = result.ranges().getFirst();
        assertEquals("Variable declaration", span.annotation);
        assertEquals(0, span.range.start().line());
        assertEquals(3, span.range.start().character());
        assertEquals(0, span.range.end().line());
        assertEquals(8, span.range.end().character());
    }
}
