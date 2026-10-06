// Copyright Strata Contributors
// SPDX-License-Identifier: Apache-2.0 OR MIT
package org.strata.jverify.langref.jls10.s10_3;

import static org.strata.jverify.JVerify.*;

// JLS 10.3 -- Array Creation of a three-dimensional array with all dimension
// expressions given; interior sub-arrays have the specified lengths.
class ArrayCreation_ThreeDim {
    static void run() {
        int[][][] a = new int[2][3][4];
        check(a[0].length == 3 && a[0][0].length == 4);
    }
}
