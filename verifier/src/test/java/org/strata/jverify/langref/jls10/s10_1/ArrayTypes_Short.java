// Copyright Strata Contributors
// SPDX-License-Identifier: Apache-2.0 OR MIT
package org.strata.jverify.langref.jls10.s10_1;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 10.1 -- Array Types: primitive element type short.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls10.s10_1.ArrayTypes_Short#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_INTERP_ERROR})
class ArrayTypes_Short {
    static void run() {
        short[] a = new short[2];
        check(a.length == 2);
    }
}
