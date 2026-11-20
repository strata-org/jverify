// Copyright Strata Contributors
// SPDX-License-Identifier: Apache-2.0 OR MIT
package org.strata.jverify.langref.jls10.s10_1;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 10.1 -- Array Types: interface reference element type Runnable[].
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls10.s10_1.ArrayTypes_Interface#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class ArrayTypes_Interface {
    static void run() {
        Runnable[] rs = new Runnable[1];
        rs[0] = () -> {};
        check(rs[0] != null);
    }
}
