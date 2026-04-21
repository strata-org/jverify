package org.strata.jverify.common;

/**
 * A position in a character buffer.
 *
 * @param line 1-indexed line number
 * @param character 1-indexed column number
 */
public record Position(int line, int character) implements Comparable<Position> {
    public Position(long line, long character) {
        this(Math.toIntExact(line), Math.toIntExact(character));
    }

    @Override
    public String toString() {
        return line + ":" + character;
    }

    @Override
    public int compareTo(Position o) {
        var lineSign = Long.compare(line, o.line);
        return lineSign == 0 ? Long.compare(character, o.character) : lineSign;
    }
}
