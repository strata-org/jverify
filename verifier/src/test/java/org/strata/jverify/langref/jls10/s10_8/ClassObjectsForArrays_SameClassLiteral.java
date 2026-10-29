// Copyright Strata Contributors
// SPDX-License-Identifier: Apache-2.0 OR MIT
package org.strata.jverify.langref.jls10.s10_8;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 10.8 -- Class Objects for Arrays: two class literals for the same array
// type refer to the same Class instance.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls10.s10_8.ClassObjectsForArrays_SameClassLiteral#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class ClassObjectsForArrays_SameClassLiteral {
    static void run() {
        check(int[].class == int[].class);
    }
}
