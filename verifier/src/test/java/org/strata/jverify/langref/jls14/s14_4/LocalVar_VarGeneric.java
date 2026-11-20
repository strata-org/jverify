// Copyright Strata Contributors
// SPDX-License-Identifier: Apache-2.0 OR MIT
package org.strata.jverify.langref.jls14.s14_4;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 14.4 -- LocalVariableDeclaration: the `var` keyword infers a
// parameterized type from the initializer expression.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls14.s14_4.LocalVar_VarGeneric#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class LocalVar_VarGeneric {
    static void run() {
        var list = new java.util.ArrayList<Integer>();
        list.add(3);
        check(list.get(0) == 3);
    }
}
