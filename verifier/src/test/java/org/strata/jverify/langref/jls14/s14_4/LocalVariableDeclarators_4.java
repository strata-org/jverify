package org.strata.jverify.langref.jls14.s14_4;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 14.4.1 -- Local Variable Declarators and Types: multiple declarators in one
// declaration, declarators with and without initializers, and the C-style array
// declarator where the brackets follow the variable name.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls14.s14_4.LocalVariableDeclarators_4#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_INTERP_ERROR})
class LocalVariableDeclarators_4 {
    static void run() {

        // A declarator may carry extra bracket pairs after its name (C-style),
        // giving it array type even though the leading type is a scalar.
        int scalar = 7, arr[] = {10, 20, 30};
        check(arr[2] == 30);
    }
}
