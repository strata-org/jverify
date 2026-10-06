// Copyright Strata Contributors
// SPDX-License-Identifier: Apache-2.0 OR MIT
package org.strata.jverify.langref.jls14.s14_3;

import static org.strata.jverify.JVerify.*;

// JLS 14.3 -- Local Class Declaration: a local interface declared in a method
// and implemented by a lambda expression.
class LocalInterface_1 {
    static void run() {
        interface I { int m(); }
        I i = () -> 7;
        check(i.m() == 7);
    }
}
