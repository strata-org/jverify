package org.strata.java.bytecode.verifier.compiler;

import org.strata.jverify.common.Position;

/**
 * Bijective (line, column) &lt;-&gt; synthetic-offset encoding.
 *
 * <p>Laurel {@link org.strata.jverify.laurel.FileRange}s and Strata's
 * diagnostic protocol speak byte offsets, but the bytecode frontend only ever
 * knows line:column (from the CharacterRangeTable) or line (from the
 * LineNumberTable) -- never byte offsets, because it never reads the source
 * text. This codec lets a line:column pair travel through the offset-shaped
 * pipeline and come back unchanged: {@link #encode} packs it into a synthetic
 * offset handed to Strata, and {@link #decode} unpacks the offset Strata
 * echoes back into the exact same {@link Position}.
 *
 * <p>The packing is {@code line * 1024 + column} -- line-major, so synthetic
 * offsets sort the same way real ones would. 1024 is not arbitrary: it is the
 * CharacterRangeTable's own column field width, so {@code encode(line, col)}
 * reproduces the classfile's native {@code line << 10 | col} packing that
 * {@code MethodBodyFactory.toSpan} unpacks with {@code s >>> 10, s & 1023}.
 * A column therefore never reaches 1024, so line and column can never bleed
 * into each other and the round trip is exact.
 *
 * <p>Trade-off (why 1024, not a decimal stride like 1_000_000): 1024 keeps a
 * synthetic offset small -- under 2^31 for any file up to ~2 million lines,
 * and comfortably within {@code long} for anything beyond -- so it never
 * overflows the pipeline. A decimal stride would make a RAW offset read like
 * its line:col in a log ({@code 2148000009} = line 2148, col 9), but offsets
 * are only ever shown to a human AFTER {@link #decode} (in the reported
 * diagnostic's range); the raw value surfaces only in debug dumps, where the
 * readability is not worth the wider, overflow-prone number.
 *
 * <p>Load-bearing assumption: Strata treats these offsets as OPAQUE tokens --
 * it stores, sorts, and echoes them, but never does arithmetic on them. If a
 * consumer ever subtracted two offsets to compute a length, the synthetic
 * values would corrupt silently. The corpus is the only guard; do not "widen"
 * a consumer to interpret an offset as a real byte index.
 */
final class PositionCodec {

    /** The CharacterRangeTable's column field width: {@code encode == line << 10 | col}. */
    private static final long STRIDE = 1024L;

    private PositionCodec() {
    }

    /** Pack a 1-indexed (line, column) into a synthetic offset. */
    static long encode(int line, int column) {
        return (long) line * STRIDE + column;
    }

    /** Unpack a synthetic offset back into its 1-indexed {@link Position}. */
    static Position decode(long offset) {
        return new Position((int) (offset / STRIDE), (int) (offset % STRIDE));
    }
}
