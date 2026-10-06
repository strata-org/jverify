// Copyright Strata Contributors
// SPDX-License-Identifier: Apache-2.0 OR MIT
package org.strata.jverify.langref.jls14.s14_3;

import static org.strata.jverify.JVerify.*;

// JLS 14.3 -- Local Class Declaration: a class declared inside a block.
class LocalClass_1 {
    static void run() {
        class Local {
            int m() { return 42; }
        }
        check(new Local().m() == 42);
    }
}
