// Copyright Strata Contributors
// SPDX-License-Identifier: Apache-2.0 OR MIT
package org.strata.jverify.langref.jls10.s10_4;

import static org.strata.jverify.JVerify.*;

// JLS 10.4 -- Array Access: reading with an index >= length throws
// ArrayIndexOutOfBoundsException.
class ArrayAccess_IobPositive {
    static void run() {
        int[] a = {1, 2};
        boolean caught = false;
        try {
            int x = a[2];
        } catch (ArrayIndexOutOfBoundsException e) {
            caught = true;
        }
        check(caught);
    }
}
