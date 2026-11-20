// Copyright Strata Contributors
// SPDX-License-Identifier: Apache-2.0 OR MIT
package org.strata.jverify.langref.jls10.s10_8;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 10.8 -- Class Objects for Arrays: distinct component types give
// distinct array Class objects, even when one is a subtype of the other.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls10.s10_8.ClassObjectsForArrays_DistinctByComponent#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class ClassObjectsForArrays_DistinctByComponent {
    static void run() {
        Class<?> a = String[].class;
        Class<?> b = Object[].class;
        check(a != b);
    }
}
