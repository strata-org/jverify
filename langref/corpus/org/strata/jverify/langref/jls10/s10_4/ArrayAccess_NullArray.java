// Copyright Strata Contributors
// SPDX-License-Identifier: Apache-2.0 OR MIT
package org.strata.jverify.langref.jls10.s10_4;

import static org.strata.jverify.JVerify.*;

// JLS 10.4 -- Array Access: indexing a null array reference throws
// NullPointerException.
class ArrayAccess_NullArray {
    static void run() {
        int[] a = null;
        boolean caught = false;
        try {
            int x = a[0];
        } catch (NullPointerException e) {
            caught = true;
        }
        check(caught);
    }
}
