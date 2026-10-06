// Copyright Strata Contributors
// SPDX-License-Identifier: Apache-2.0 OR MIT
package org.strata.jverify.langref.jls14.s14_30;

import static org.strata.jverify.JVerify.*;

// JLS 14.30 -- Record pattern with `var` component patterns; the compiler
// infers each component's type from the record definition.
class RecordPattern_Var {
    record P(int x, int y) {}

    static void run() {
        Object o = new P(3, 4);
        int r;
        if (o instanceof P(var a, var b)) {
            r = a + b;
        } else {
            r = -1;
        }
        check(r == 7);
    }
}
