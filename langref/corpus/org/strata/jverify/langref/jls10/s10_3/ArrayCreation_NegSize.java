// Copyright Strata Contributors
// SPDX-License-Identifier: Apache-2.0 OR MIT
package org.strata.jverify.langref.jls10.s10_3;

import static org.strata.jverify.JVerify.*;

// JLS 10.3 -- Array Creation with a negative dimension expression throws
// NegativeArraySizeException at run time.
class ArrayCreation_NegSize {
    static void run() {
        boolean caught = false;
        try {
            int[] a = new int[-1];
        } catch (NegativeArraySizeException e) {
            caught = true;
        }
        check(caught);
    }
}
