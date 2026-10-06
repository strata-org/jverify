// Copyright Strata Contributors
// SPDX-License-Identifier: Apache-2.0 OR MIT
package org.strata.jverify.langref.jls14.s14_14;

import static org.strata.jverify.JVerify.*;

// JLS 14.14.1 -- Basic for statement with multiple init and update expressions
// separated by commas.
class ForMultipleInit_1 {
    static void run() {
        int r = 0;
        for (int i = 0, j = 10; i < j; i++, j--) {
            r++;
        }
        check(r == 5);
    }
}
