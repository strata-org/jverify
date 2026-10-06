// Copyright Strata Contributors
// SPDX-License-Identifier: Apache-2.0 OR MIT
package org.strata.jverify.langref.jls10.s10_1;

import static org.strata.jverify.JVerify.*;

// JLS 10.1 -- Array Types: primitive element type double.
class ArrayTypes_Double {
    static void run() {
        double[] a = new double[2];
        check(a.length == 2);
    }
}
