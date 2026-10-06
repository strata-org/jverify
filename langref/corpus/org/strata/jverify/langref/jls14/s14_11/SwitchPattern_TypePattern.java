// Copyright Strata Contributors
// SPDX-License-Identifier: Apache-2.0 OR MIT
package org.strata.jverify.langref.jls14.s14_11;

import static org.strata.jverify.JVerify.*;

// JLS 14.11 -- Switch statement: switch with type patterns as case labels.
class SwitchPattern_TypePattern {
    static void run() {
        Object o = "hi";
        int r;
        switch (o) {
            case Integer i -> r = i;
            case String s -> r = s.length();
            default -> r = -1;
        }
        check(r == 2);
    }
}
