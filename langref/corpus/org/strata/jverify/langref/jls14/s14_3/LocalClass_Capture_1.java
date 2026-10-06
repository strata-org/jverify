// Copyright Strata Contributors
// SPDX-License-Identifier: Apache-2.0 OR MIT
package org.strata.jverify.langref.jls14.s14_3;

import static org.strata.jverify.JVerify.*;

// JLS 14.3 -- Local Class Declaration: a local class captures an effectively
// final enclosing local variable.
class LocalClass_Capture_1 {
    static void run() {
        int cap = 5;
        class Local {
            int m() { return cap; }
        }
        check(new Local().m() == 5);
    }
}
