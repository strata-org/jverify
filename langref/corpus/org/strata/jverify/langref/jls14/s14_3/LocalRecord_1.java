// Copyright Strata Contributors
// SPDX-License-Identifier: Apache-2.0 OR MIT
package org.strata.jverify.langref.jls14.s14_3;

import static org.strata.jverify.JVerify.*;

// JLS 14.3 -- Local Class Declaration: a local record declared in a method.
class LocalRecord_1 {
    static void run() {
        record P(int x, int y) {}
        check(new P(1, 2).y() == 2);
    }
}
