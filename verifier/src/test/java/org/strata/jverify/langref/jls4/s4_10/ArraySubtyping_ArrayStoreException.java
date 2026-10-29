package org.strata.jverify.langref.jls4.s4_10;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 4.10.3 (via JLS 10.10) -- Storing a value whose runtime type is not
// assignable to the actual array component type throws ArrayStoreException.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls4.s4_10.ArraySubtyping_ArrayStoreException#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class ArraySubtyping_ArrayStoreException {
    static void run() {

        Object[] o = new Integer[1];
        boolean caught = false;
        try {
            o[0] = "x";
        } catch (ArrayStoreException e) {
            caught = true;
        }
        check(caught);
    }
}
