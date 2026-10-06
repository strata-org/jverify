// Copyright Strata Contributors
// SPDX-License-Identifier: Apache-2.0 OR MIT
package org.strata.jverify.langref.jls10.s10_1;

import static org.strata.jverify.JVerify.*;

// JLS 10.1 -- Array Types: primitive element type byte.
class ArrayTypes_Byte {
    static void run() {
        byte[] a = new byte[2];
        check(a.length == 2);
    }
}
