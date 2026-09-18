package org.strata.jverify.verifier.tests.javasupport.expressions;

/**
 * Companion to {@link PrimitiveCastsMultiFile}. Uses the same
 * {@code (short) int} cast so the two files together exercise the
 * cast-helper deduplication path: without deduplication the
 * `$cast_to_int16_from_int32` procedure would be emitted twice and
 * Strata would reject the duplicate at (1:1-1:1).
 */
class PrimitiveCastsSecondFile {
    static short castHereToo() {
        int x = 100;
        return (short) x;
    }
}
