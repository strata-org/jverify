package org.strata.jverify.common;

public record Range(Position start, Position end) implements Comparable<Range> {
    @Override
    public String toString() {
        return start.toString() + "-" + end.toString();
    }

    @Override
    public int compareTo(Range o) {
        var startSign = start.compareTo(o.start);
        return startSign == 0 ? end.compareTo(o.end) : startSign;
    }
}
