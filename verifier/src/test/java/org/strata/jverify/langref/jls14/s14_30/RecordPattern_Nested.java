// Copyright Strata Contributors
// SPDX-License-Identifier: Apache-2.0 OR MIT
package org.strata.jverify.langref.jls14.s14_30;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 14.30 -- Nested record patterns: component patterns can themselves be
// record patterns, allowing deep destructuring in a single instanceof.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls14.s14_30.RecordPattern_Nested#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class RecordPattern_Nested {
    record Pt(int x, int y) {}
    record Ln(Pt a, Pt b) {}

    static void run() {
        Object o = new Ln(new Pt(1, 2), new Pt(3, 4));
        int r;
        if (o instanceof Ln(Pt(int x1, int y1), Pt(int x2, int y2))) {
            r = x1 + y1 + x2 + y2;
        } else {
            r = -1;
        }
        check(r == 10);
    }
}
