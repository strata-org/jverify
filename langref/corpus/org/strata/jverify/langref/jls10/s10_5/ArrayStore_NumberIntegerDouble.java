// Copyright Strata Contributors
// SPDX-License-Identifier: Apache-2.0 OR MIT
package org.strata.jverify.langref.jls10.s10_5;

import static org.strata.jverify.JVerify.*;

// JLS 10.5 -- ArrayStoreException: storing a Double through a Number[]
// reference that actually points to an Integer[] fails at run time.
class ArrayStore_NumberIntegerDouble {
    static void run() {
        Number[] n = new Integer[1];
        boolean caught = false;
        try {
            n[0] = Double.valueOf(1.0);
        } catch (ArrayStoreException e) {
            caught = true;
        }
        check(caught);
    }
}
