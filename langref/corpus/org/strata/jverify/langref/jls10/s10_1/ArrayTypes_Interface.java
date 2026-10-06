// Copyright Strata Contributors
// SPDX-License-Identifier: Apache-2.0 OR MIT
package org.strata.jverify.langref.jls10.s10_1;

import static org.strata.jverify.JVerify.*;

// JLS 10.1 -- Array Types: interface reference element type Runnable[].
class ArrayTypes_Interface {
    static void run() {
        Runnable[] rs = new Runnable[1];
        rs[0] = () -> {};
        check(rs[0] != null);
    }
}
