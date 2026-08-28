package org.strata.java.bytecode.frontend;


/**
 * One {@code CharacterRangeTable} entry with the {@code statement} flag, resolved to its bytecode
 * pc window plus source {@link Span}. Statement ranges nest (a statement inside a block inside a
 * method), so a lookup picks the NARROWEST range covering a pc -- the innermost source statement.
 */
public record CrtRange(int startPc, int endPc, Span span) {
    int pcWidth() { return endPc - startPc; }
    /**
     * Source-extent tie-break between ranges with an identical pc window:
     * fewer lines wins, then fewer columns. Lexicographic on purpose — the
     * column span of a multi-line range can be negative (the end column
     * lives on a later line), so the two dimensions must not be mixed
     * arithmetically.
     */
    boolean narrowerSrcThan(CrtRange other) {
        int lines = span.endLine() - span.beginLine();
        int otherLines = other.span.endLine() - other.span.beginLine();
        if (lines != otherLines) {
            return lines < otherLines;
        }
        return span.endCol() - span.beginCol() < other.span.endCol() - other.span.beginCol();
    }
    boolean covers(int pc) { return pc >= startPc && pc <= endPc; }
}
