// Copyright Strata Contributors
// SPDX-License-Identifier: Apache-2.0 OR MIT
package org.strata.jverify.langref.jls14.s14_14;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 14.14.1 -- Basic for statement with all three header clauses empty
// (for(;;)), terminated by a break inside the body.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls14.s14_14.ForEmpty_1#run")
class ForEmpty_1 {
    static void run() {
        int i = 0;
        for (;;) {
            if (i == 3) break;
            i++;
        }
        check(i == 3);
    }
}
