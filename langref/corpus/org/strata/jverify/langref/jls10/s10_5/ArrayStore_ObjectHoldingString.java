// Copyright Strata Contributors
// SPDX-License-Identifier: Apache-2.0 OR MIT
package org.strata.jverify.langref.jls10.s10_5;

import static org.strata.jverify.JVerify.*;

// JLS 10.5 -- ArrayStoreException: storing an Integer through an Object[]
// reference that actually points to a String[] fails at run time.
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
