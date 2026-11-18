package org.strata.jverify.verifier.tests.interpret;

import org.strata.jverify.InterpretEntry;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * {@code Boolean.TRUE} holds true and {@code Boolean.FALSE} false: the static
 * initializer of the contract of {@code Boolean} makes each from its value.
 */
@JVerifyTest(
        interpret = true,
        expectedToFail = true,
        blockedBy = {Blocker.STRATA_OTHER},
        exitCode = 0,
        errorCount = 0)
class InterpretBooleanConstantValues {
    @InterpretEntry
    static void run() {
        check(Boolean.TRUE.booleanValue());
        check(!Boolean.FALSE.booleanValue());
        check(Boolean.TRUE.equals(Boolean.TRUE));
        check(!Boolean.TRUE.equals(Boolean.FALSE));
        Boolean selected = Boolean.TRUE;
        check(selected ? true : false);
    }
}
