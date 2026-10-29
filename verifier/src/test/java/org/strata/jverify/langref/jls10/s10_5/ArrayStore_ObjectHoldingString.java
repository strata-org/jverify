// Copyright Strata Contributors
// SPDX-License-Identifier: Apache-2.0 OR MIT
package org.strata.jverify.langref.jls10.s10_5;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 10.5 -- ArrayStoreException: storing an Integer through an Object[]
// reference that actually points to a String[] fails at run time.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls10.s10_5.ArrayStore_ObjectHoldingString#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class ArrayStore_ObjectHoldingString {
    static void run() {
        Object[] o = new String[1];
        boolean caught = false;
        try {
            o[0] = Integer.valueOf(1);
        } catch (ArrayStoreException e) {
            caught = true;
        }
        check(caught);
    }
}
