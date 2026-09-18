package org.strata.java.bytecode.frontend;


/** A source line:col range recovered from the CharacterRangeTable. */
public record Span(int beginLine, int beginCol, int endLine, int endCol) {
    public static final Span NONE = new Span(0, 0, 0, 0);
    public boolean isNone() { return equals(NONE); }
    @Override public String toString() { return beginLine + ":" + beginCol + "-" + endLine + ":" + endCol; }
}
