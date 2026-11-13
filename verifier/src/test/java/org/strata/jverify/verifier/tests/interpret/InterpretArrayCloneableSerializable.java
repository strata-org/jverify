package org.strata.jverify.verifier.tests.interpret;

import java.io.Serializable;

import org.strata.jverify.InterpretEntry;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * Every array type implements {@code Cloneable} and {@code java.io.Serializable}:
 * an array is an instance of both, a cast to either gives the same array, and a
 * cast to any other interface throws {@code ClassCastException}. An object of a
 * class that implements neither is an instance of neither.
 */
@JVerifyTest(
        interpret = true,
        expectedToFail = true,
        blockedBy = {Blocker.STRATA_ARRAYS},
        exitCode = 0,
        methodsVerified = 3,
        methodsInvalid = 0,
        methodsSkipped = 0,
        errorCount = 0)
class InterpretArrayCloneableSerializable {
    static class Other {
    }

    @InterpretEntry
    static void run() {
        Object ints = new int[1];
        check(ints instanceof Cloneable);
        check(ints instanceof Serializable);
        check(!(ints instanceof Runnable));

        Object longs = new long[2];
        check(longs instanceof Cloneable);
        check(longs instanceof Serializable);

        Object plain = new Other();
        check(!(plain instanceof Cloneable));
        check(!(plain instanceof Serializable));

        Cloneable cloneable = (Cloneable) ints;
        Serializable serializable = (Serializable) ints;
        check(cloneable == ints && serializable == ints);
        ((int[]) cloneable)[0] = 3;
        check(((int[]) serializable)[0] == 3);

        boolean caught = false;
        try {
            Runnable wrong = (Runnable) ints;
            check(false);
        } catch (ClassCastException e) {
            caught = true;
        }
        check(caught);
    }
}
