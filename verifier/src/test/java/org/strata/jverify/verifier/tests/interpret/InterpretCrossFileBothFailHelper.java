package org.strata.jverify.verifier.tests.interpret;

import org.strata.jverify.InterpretEntry;

import static org.strata.jverify.JVerify.*;

/**
 * Helper for {@link InterpretCrossFileBothFail}. Carries its own
 * {@code @InterpretEntry} with a failing assertion, so the interpret run
 * executes two entries -- one per input file -- and must report a diagnostic
 * against each. The caret below annotates this file's own failure.
 * <p>The comment padding further down is load-bearing: it aligns this file's
 * assertion to the same byte offset as the one in
 * {@code InterpretCrossFileBothFail.java}. Laurel names a generated assert after
 * its source position, and when that position was the byte offset alone, two
 * asserts at equal offsets in different files collided on one label -- only the
 * last survived in {@code collectAssertInfo}, so one diagnostic was lost and the
 * survivor was attributed to the wrong file. Labels now include the file. Keep
 * the offsets equal or this test stops covering the collision.
 * (offset alignment padding: keeps this file's assertion at the same byte offset as the marked file's)            
 * (offset alignment padding: keeps this file's assertion at the same byte offset as the marked file's)           
 * (offset alignment padding: keeps this file's assertion at the same byte offset as the marked file's)           
 * (offset alignment padding: keeps this file's assertion at the same byte offset as the marked file's)           
 * (offset alignment padding: keeps this file's assertion at the same byte offset as the marked file's)           
 */
class InterpretCrossFileBothFailHelper {
    @InterpretEntry
    static void alsoFails() {
        check(false);
//      ^^^^^^^^^^^^ Error: assertion does not hold
    }
}
