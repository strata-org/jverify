// Copyright Strata Contributors
// SPDX-License-Identifier: Apache-2.0 OR MIT
package org.strata.jverify.langref.jls14.s14_11;

import static org.strata.jverify.JVerify.*;

// JLS 14.11 -- Switch statement: String selector expression.
class SwitchOnString_1 {
    static void run() {
        String s = "b";
        int r;
        switch (s) {
            case "a": r = 1; break;
            case "b": r = 2; break;
            default: r = 0;
        }
        check(r == 2);
    }
}
