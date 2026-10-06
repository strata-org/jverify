// Copyright Strata Contributors
// SPDX-License-Identifier: Apache-2.0 OR MIT
package org.strata.jverify.langref.jls14.s14_4;

import static org.strata.jverify.JVerify.*;

// JLS 14.4 -- LocalVariableDeclaration: the `var` keyword infers the loop
// variable's type from the array element type in an enhanced for statement.
class LocalVar_VarInForEach {
    static void run() {
        int[] a = {10, 20, 30};
        int sum = 0;
        for (var v : a) {
            sum += v;
        }
        check(sum == 60);
    }
}
