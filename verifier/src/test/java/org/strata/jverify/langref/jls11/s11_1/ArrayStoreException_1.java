package org.strata.jverify.langref.jls11.s11_1;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 11.1.2 -- ArrayStoreException when storing an element into an array
// whose runtime component type is incompatible.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls11.s11_1.ArrayStoreException_1#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class ArrayStoreException_1 {
    static void run() {
        Object[] o = new String[1];
        boolean caught = false;
        try {
            o[0] = Integer.valueOf(42);
        } catch (ArrayStoreException e) {
            caught = true;
        }
        check(caught);
    }
}
