package org.strata.java.bytecode.frontend;


/**
 * One {@code CharacterRangeTable} entry with the {@code statement} flag, resolved to its bytecode
 * pc window plus source {@link Span}. Statement ranges nest (a statement inside a block inside a
 * method), so a lookup picks the NARROWEST range covering a pc -- the innermost source statement.
 */
public record CrtRange(int startPc, int endPc, Span span) {
    int pcWidth() { return endPc - startPc; }
    /** Source extent, used to break ties between ranges with an identical pc window. */
    int srcWidth() { return (span.endLine() - span.beginLine()) * 1000 + (span.endCol() - span.beginCol()); }
    boolean covers(int pc) { return pc >= startPc && pc <= endPc; }
}
