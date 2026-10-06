// Copyright Strata Contributors
// SPDX-License-Identifier: Apache-2.0 OR MIT
package org.strata.jverify.langref.jls10.s10_8;

import static org.strata.jverify.JVerify.*;

// JLS 10.8 -- Class Objects for Arrays: an array instance's getClass() returns
// the array type's Class object.
class ClassObjectsForArrays_GetClass {
    static void run() {
        int[] a = new int[3];
        check(a.getClass() == int[].class);
    }
}
