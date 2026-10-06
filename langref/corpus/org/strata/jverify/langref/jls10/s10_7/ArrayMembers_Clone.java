// Copyright Strata Contributors
// SPDX-License-Identifier: Apache-2.0 OR MIT
package org.strata.jverify.langref.jls10.s10_7;

import static org.strata.jverify.JVerify.*;

// JLS 10.7 -- Array Members: clone() returns a fresh array with the same
// component values; mutating the clone does not affect the original.
class ArrayMembers_Clone {
    static void run() {
        int[] a = {1, 2};
        int[] b = a.clone();
        b[0] = 99;
        check(a[0] == 1 && b[0] == 99);
    }
}
