// Copyright Strata Contributors
// SPDX-License-Identifier: Apache-2.0 OR MIT
package org.strata.jverify.langref.jls14.s14_19;

import static org.strata.jverify.JVerify.*;

// JLS 14.19 -- Synchronized statement: evaluating a null lock expression
// throws NullPointerException before entering the block.
class Synchronized_NullNPE {
    static void run() {
        Object lock = null;
        boolean caught = false;
        try {
            synchronized (lock) {}
        } catch (NullPointerException e) {
            caught = true;
        }
        check(caught);
    }
}
