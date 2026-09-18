package org.strata.java.bytecode.verifier.laurel;

import org.strata.jverify.common.Position;

import java.net.URI;

/**
 * Resolves a Strata diagnostic's echoed offset back into a source
 * {@link Position}.
 *
 * <p>The bytecode frontend's offsets are synthetic (line, column) packings
 * produced by {@code PositionCodec} -- {@code line * 1_000_000 + column} --
 * which exceed {@link Integer#MAX_VALUE} from source line 2148 on. The shared
 * {@code FilesMap} interface is {@code int}-typed (JFE's real byte offsets),
 * so this frontend threads {@code long} end to end instead; Laurel's
 * {@code Raw} carries a {@code long} already.
 *
 * <p>Implementations MUST throw a {@link RuntimeException} for a URI that was
 * never a frontend input, so the diagnostic parser degrades it to a counted
 * malformed-diagnostic instead of attributing a fabricated position to a
 * phantom file.
 */
@FunctionalInterface
public interface PositionResolver {
    Position positionOf(URI file, long offset);
}
