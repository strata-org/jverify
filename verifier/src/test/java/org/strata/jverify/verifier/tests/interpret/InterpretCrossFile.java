package org.strata.jverify.verifier.tests.interpret;

import org.strata.jverify.InterpretEntry;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * Multi-file interpret test: "combines multiple input files" is part of
 * {@code laurelInterpretBinary}'s contract, so exercise it. The @InterpretEntry
 * method here calls a helper defined in {@code InterpretCrossFileHelper.java},
 * and the failing {@code check} stays in <i>this</i> file so the caret
 * annotation below applies to it.
 *
 * <p>This pins the per-file offset-to-position mapping through {@code filesMap}:
 * the diagnostic must resolve against the correct file, not merely the correct
 * offset. It is also the case that would regress if assert labels — which are
 * built from a byte offset alone, with no file — were allowed to collide across
 * input files.
 */
@JVerifyTest(
        interpret = true,
        exitCode = 4,
        methodsVerified = 3,
        methodsInvalid = 1,
        errorCount = 1,
        additionalFiles = { "./InterpretCrossFileHelper.java" })
class InterpretCrossFile {
    @InterpretEntry
    static void run() {
        int y = InterpretCrossFileHelper.doubleIt(5);
        check(y == 0);
//      ^^^^^^^^^^^^^ Error: assertion does not hold
    }
}
