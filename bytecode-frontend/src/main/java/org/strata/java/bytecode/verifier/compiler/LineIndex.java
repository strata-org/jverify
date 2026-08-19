package org.strata.java.bytecode.verifier.compiler;

import org.strata.jverify.common.Position;

import java.util.ArrayList;
import java.util.List;

/**
 * Line/column to byte-offset conversion (and back) for one source file.
 *
 * <p>Laurel {@code FileRange}s carry raw byte offsets and Strata echoes them
 * back in diagnostics; the CharacterRangeTable speaks 1-indexed line:col.
 * This bridges the two, replacing javac's {@code LineMap} in JFE.
 */
public final class LineIndex {
    private final int[] lineStarts;
    private final int length;

    public LineIndex(String content) {
        List<Integer> starts = new ArrayList<>();
        starts.add(0);
        for (int i = 0; i < content.length(); i++) {
            if (content.charAt(i) == '\n') {
                starts.add(i + 1);
            }
        }
        this.lineStarts = starts.stream().mapToInt(Integer::intValue).toArray();
        this.length = content.length();
    }

    /** Byte offset of a 1-indexed (line, col) position. */
    public int offset(int line, int col) {
        int lineStart = lineStarts[Math.min(line, lineStarts.length) - 1];
        return Math.min(lineStart + col - 1, length);
    }

    /** 1-indexed position of a byte offset; mirrors JFE's FilesMap math. */
    public Position position(int offset) {
        int lo = 0;
        int hi = lineStarts.length - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (lineStarts[mid] <= offset) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return new Position(lo + 1, offset - lineStarts[lo] + 1);
    }
}
