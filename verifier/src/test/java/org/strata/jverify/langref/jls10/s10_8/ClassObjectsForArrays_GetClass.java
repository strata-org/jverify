// Copyright Strata Contributors
// SPDX-License-Identifier: Apache-2.0 OR MIT
package org.strata.jverify.langref.jls10.s10_8;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 10.8 -- Class Objects for Arrays: an array instance's getClass() returns
// the array type's Class object.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls10.s10_8.ClassObjectsForArrays_GetClass#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class ClassObjectsForArrays_GetClass {
    static void run() {
        int[] a = new int[3];
        check(a.getClass() == int[].class);
    }
}
