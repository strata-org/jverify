package org.strata.jverify.langref.jls14.s14_4;

import static org.strata.jverify.JVerify.*;

// JLS 14.4.1 -- Local Variable Declarators and Types: multiple declarators in one
// declaration, declarators with and without initializers, and the C-style array
// declarator where the brackets follow the variable name.
class LocalVariableDeclarators_6 {
    static void run() {

        // Mixing element-type brackets and declarator brackets in one declaration.
        int[] xs = {1, 2}, ys[] = {{3, 4}, {5, 6}};
        check(ys[1][0] == 5);
    }
}
