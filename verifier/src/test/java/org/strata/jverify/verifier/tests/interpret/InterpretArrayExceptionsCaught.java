package org.strata.jverify.verifier.tests.interpret;

import org.strata.jverify.InterpretEntry;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * An index below zero or at the length throws
 * {@code ArrayIndexOutOfBoundsException}, and a negative length throws
 * {@code NegativeArraySizeException}; a handler for the exception or for a
 * superclass catches them, and the failed store writes nothing.
 */
@JVerifyTest(
        interpret = true,
        expectedToFail = true,
        blockedBy = {Blocker.STRATA_ARRAYS},
        exitCode = 0,
        methodsVerified = 2,
        methodsInvalid = 0,
        methodsSkipped = 0,
        errorCount = 0)
class InterpretArrayExceptionsCaught {
    @InterpretEntry
    static void run() {
        int[] a = new int[2];
        int caught = 0;
        try {
            a[2] = 1;
            check(false);
        } catch (ArrayIndexOutOfBoundsException e) {
            caught++;
        }
        try {
            int x = a[-1];
            check(false);
        } catch (ArrayIndexOutOfBoundsException e) {
            caught++;
        }
        try {
            a[-1] = 1;
            check(false);
        } catch (RuntimeException e) {
            caught++;
        }
        int length = -1;
        try {
            int[] b = new int[length];
            check(false);
        } catch (NegativeArraySizeException e) {
            caught++;
        }
        check(caught == 4);
        check(a[0] == 0 && a[1] == 0);
    }
}
