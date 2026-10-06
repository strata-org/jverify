// Copyright Strata Contributors
// SPDX-License-Identifier: Apache-2.0 OR MIT
package org.strata.jverify.langref.jls10.s10_1;

import static org.strata.jverify.JVerify.*;

// JLS 10.1 -- Array Types: three-dimensional array type int[][][].
class ArrayTypes_ThreeDim {
    static void run() {
        int[][][] a = new int[2][3][4];
        check(a[1][2][3] == 0);
    }
}
