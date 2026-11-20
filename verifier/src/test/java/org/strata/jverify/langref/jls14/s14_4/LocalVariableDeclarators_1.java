package org.strata.jverify.langref.jls14.s14_4;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 14.4.1 -- Local Variable Declarators and Types: multiple declarators in one
// declaration, declarators with and without initializers, and the C-style array
// declarator where the brackets follow the variable name.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls14.s14_4.LocalVariableDeclarators_1#run")
class LocalVariableDeclarators_1 {
    static void run() {

        // Several declarators of the same type in one declaration statement.
        int a = 1, b = 2, c;
        c = a + b;
        check(c == 3);
    }
}
