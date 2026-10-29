// Copyright Strata Contributors
// SPDX-License-Identifier: Apache-2.0 OR MIT
package org.strata.jverify.langref.jls14.s14_11;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 14.11 -- Switch statement: record pattern as case label deconstructs
// the record's components.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls14.s14_11.SwitchPattern_RecordPattern#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class SwitchPattern_RecordPattern {
    record P(int x, int y) {}

    static void run() {
        Object o = new P(3, 4);
        int r;
        switch (o) {
            case P(int x, int y) -> r = x + y;
            default -> r = -1;
        }
        check(r == 7);
    }
}
