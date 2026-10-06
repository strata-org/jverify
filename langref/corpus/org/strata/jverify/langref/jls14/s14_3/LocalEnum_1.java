// Copyright Strata Contributors
// SPDX-License-Identifier: Apache-2.0 OR MIT
package org.strata.jverify.langref.jls14.s14_3;

import static org.strata.jverify.JVerify.*;

// JLS 14.3 -- Local Class Declaration: a local enum declared in a method.
class LocalEnum_1 {
    static void run() {
        enum E { A, B; }
        check(E.B.ordinal() == 1);
    }
}
