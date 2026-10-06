// Copyright Strata Contributors
// SPDX-License-Identifier: Apache-2.0 OR MIT
package org.strata.jverify.langref.jls14.s14_18;

import static org.strata.jverify.JVerify.*;

// JLS 14.18 -- Throw statement: throwing a null Throwable reference throws
// NullPointerException at run time.
class Throw_NullThrows {
    static void run() {
        RuntimeException e = null;
        boolean caught = false;
        try {
            throw e;
        } catch (NullPointerException npe) {
            caught = true;
        }
        check(caught);
    }
}
