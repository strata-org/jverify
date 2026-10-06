// Copyright Strata Contributors
// SPDX-License-Identifier: Apache-2.0 OR MIT
package org.strata.jverify.langref.jls14.s14_4;

import static org.strata.jverify.JVerify.*;

// JLS 14.4 -- LocalVariableDeclaration: the `var` keyword infers a
// parameterized type from the initializer expression.
class LocalVar_VarGeneric {
    static void run() {
        var list = new java.util.ArrayList<Integer>();
        list.add(3);
        check(list.get(0) == 3);
    }
}
