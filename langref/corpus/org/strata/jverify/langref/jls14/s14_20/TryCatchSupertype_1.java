// Copyright Strata Contributors
// SPDX-License-Identifier: Apache-2.0 OR MIT
package org.strata.jverify.langref.jls14.s14_20;

import static org.strata.jverify.JVerify.*;

// JLS 14.20.1 -- Try-catch: a catch clause whose parameter type is a supertype
// of the thrown exception's runtime class handles the throw.
class TryCatchSupertype_1 {
    static void run() {
        boolean caught = false;
        try {
            throw new IllegalStateException("x");
        } catch (RuntimeException e) {
            caught = true;
        }
        check(caught);
    }
}
