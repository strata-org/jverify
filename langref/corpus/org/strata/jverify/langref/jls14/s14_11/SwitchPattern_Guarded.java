// Copyright Strata Contributors
// SPDX-License-Identifier: Apache-2.0 OR MIT
package org.strata.jverify.langref.jls14.s14_11;

import static org.strata.jverify.JVerify.*;

// JLS 14.11 -- Switch statement: guarded pattern label using `when` clause.
class SwitchPattern_Guarded {
    static void run() {
        Object o = 7;
        int r;
        switch (o) {
            case Integer i when i > 0 -> r = 1;
            case Integer i -> r = -1;
            default -> r = 0;
        }
        check(r == 1);
    }
}
