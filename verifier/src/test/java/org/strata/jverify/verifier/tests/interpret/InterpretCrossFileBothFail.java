package org.strata.jverify.verifier.tests.interpret;

import org.strata.jverify.InterpretEntry;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * Multi-file interpret test where <em>both</em> input files contain a failing
 * assertion, so each diagnostic must be attributed to its own file. Each file
 * carries the caret for its own failure.
 *
 * <p>This is the regression test for the assert-label collision. Laurel names a
 * generated assert after its source position; when that position was the byte
 * offset alone, two asserts in different input files could share a label and
 * {@code collectAssertInfo} kept only the last one — one diagnostic was lost and
 * the survivor was attributed to whichever file was translated last. Labels now
 * carry the file, so the two failures stay distinct.
 *
 * <p>Two details make the collision reachable:
 * <ul>
 *   <li>Each file marks its own {@code @InterpretEntry}. The interpreter aborts
 *       an entry at its first failed assertion, so one entry could only ever
 *       yield one diagnostic; two entries are what make both failures
 *       observable at once.</li>
 *   <li>The two assertions sit at the <em>same byte offset</em> (the helper pads
 *       its header to line them up). At differing offsets the labels were
 *       already distinct and the bug never showed.</li>
 * </ul>
 */
@JVerifyTest(
        interpret = true,
        exitCode = 4,
        methodsVerified = 2,
        methodsInvalid = 2,
        errorCount = 2,
        additionalFiles = { "./InterpretCrossFileBothFailHelper.java" })
class InterpretCrossFileBothFail {
    @InterpretEntry
    static void run() {
        check(false);
//      ^^^^^^^^^^^^ Error: assertion does not hold
    }
}
