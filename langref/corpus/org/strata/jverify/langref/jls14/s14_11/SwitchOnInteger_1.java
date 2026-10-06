// Copyright Strata Contributors
// SPDX-License-Identifier: Apache-2.0 OR MIT
package org.strata.jverify.langref.jls14.s14_11;

import static org.strata.jverify.JVerify.*;

// JLS 14.11 -- Switch statement: boxed Integer selector expression (unboxed
// to int at run time).
class SwitchOnInteger_1 {
    static void run() {
        Integer x = 2;
        int r;
        switch (x) {
            case 1: r = 10; break;
            case 2: r = 20; break;
            default: r = 0;
        }
        check(r == 20);
    }
}
