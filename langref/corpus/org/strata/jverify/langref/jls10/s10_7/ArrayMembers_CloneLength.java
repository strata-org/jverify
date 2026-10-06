// Copyright Strata Contributors
// SPDX-License-Identifier: Apache-2.0 OR MIT
package org.strata.jverify.langref.jls10.s10_7;

import static org.strata.jverify.JVerify.*;

// JLS 10.7 -- Array Members: clone() preserves the length of the original.
class ArrayMembers_CloneLength {
    static void run() {
        int[] a = new int[5];
        int[] b = a.clone();
        check(b.length == 5);
    }
}
