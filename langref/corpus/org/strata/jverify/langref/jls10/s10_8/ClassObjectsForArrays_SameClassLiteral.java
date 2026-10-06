// Copyright Strata Contributors
// SPDX-License-Identifier: Apache-2.0 OR MIT
package org.strata.jverify.langref.jls10.s10_8;

import static org.strata.jverify.JVerify.*;

// JLS 10.8 -- Class Objects for Arrays: two class literals for the same array
// type refer to the same Class instance.
class ClassObjectsForArrays_SameClassLiteral {
    static void run() {
        check(int[].class == int[].class);
    }
}
