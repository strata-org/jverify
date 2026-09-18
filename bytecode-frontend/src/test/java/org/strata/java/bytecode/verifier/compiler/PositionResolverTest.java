package org.strata.java.bytecode.verifier.compiler;

import org.junit.jupiter.api.Test;
import org.strata.jverify.common.Position;

import java.net.URI;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Exercises the REAL production resolver ({@link BytecodeToLaurelCompiler#positionResolver})
 * and {@link PositionCodec} together -- not a hand-copied stand-in -- so a
 * change to either the URI-validation rule or the codec's packing is caught
 * here.
 */
class PositionResolverTest {

    private static final URI INPUT = URI.create("file:///work/Demo.java");
    private static final URI PHANTOM = URI.create("file:///phantom/Other.java");

    @Test
    void codecRoundTripsThroughTheResolver() {
        var resolver = BytecodeToLaurelCompiler.positionResolver(Set.of(INPUT));
        assertEquals(new Position(3, 9),
                resolver.positionOf(INPUT, PositionCodec.encode(3, 9)));
        // A line whose encoded offset exceeds Integer.MAX_VALUE (STRIDE * line
        // > 2^31) still resolves -- the offset is a long end to end.
        assertEquals(new Position(3_000_000, 5),
                resolver.positionOf(INPUT, PositionCodec.encode(3_000_000, 5)));
    }

    @Test
    void encodedOffsetForALargeLineExceedsIntRange() {
        // Guards the reason the pipeline is long-typed: a realistic-ish large
        // file already overflows int once packed.
        long offset = PositionCodec.encode(3_000_000, 5);
        org.junit.jupiter.api.Assertions.assertTrue(offset > Integer.MAX_VALUE,
                "expected encoded offset to exceed Integer.MAX_VALUE, was " + offset);
    }

    @Test
    void unregisteredUriThrows() {
        var resolver = BytecodeToLaurelCompiler.positionResolver(Set.of(INPUT));
        assertThrows(RuntimeException.class,
                () -> resolver.positionOf(PHANTOM, PositionCodec.encode(3, 9)));
    }
}
