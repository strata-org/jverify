package org.strata.java.bytecode.frontend;

import java.lang.classfile.Attributes;
import java.lang.classfile.attribute.CodeAttribute;

/**
 * One method's LineNumberTable, resolved to bytecode pcs: the position
 * fallback when the input was compiled without {@code -Xjcov} (no
 * CharacterRangeTable). Line precision only -- the LNT has no columns and no
 * statement extents.
 */
public final class LineNumbers {

    private final int[] startPcs;   // ascending
    private final int[] lines;
    private final int minLine;
    private final int maxLine;

    private LineNumbers(int[] startPcs, int[] lines, int minLine, int maxLine) {
        this.startPcs = startPcs;
        this.lines = lines;
        this.minLine = minLine;
        this.maxLine = maxLine;
    }

    /** The method's LineNumberTable, or null when absent. */
    public static LineNumbers of(CodeAttribute code) {
        var table = code.findAttribute(Attributes.lineNumberTable()).orElse(null);
        if (table == null || table.lineNumbers().isEmpty()) {
            return null;
        }
        var entries = new java.util.ArrayList<>(table.lineNumbers());
        entries.sort(java.util.Comparator.comparingInt(e -> e.startPc()));
        int[] pcs = new int[entries.size()];
        int[] lns = new int[entries.size()];
        int min = Integer.MAX_VALUE;
        int max = -1;
        for (int i = 0; i < entries.size(); i++) {
            pcs[i] = entries.get(i).startPc();
            lns[i] = entries.get(i).lineNumber();
            min = Math.min(min, lns[i]);
            max = Math.max(max, lns[i]);
        }
        return new LineNumbers(pcs, lns, min, max);
    }

    /** The source line of the LNT region covering {@code bci}, or -1 before the first entry. */
    public int lineAt(int bci) {
        int at = -1;
        for (int i = 0; i < startPcs.length && startPcs[i] <= bci; i++) {
            at = lines[i];
        }
        return at;
    }

    public int minLine() {
        return minLine;
    }

    public int maxLine() {
        return maxLine;
    }
}
