package org.strata.jverify.langref.jls14.s14_4;

import static org.strata.jverify.JVerify.*;

// JLS 14.4.1 -- Local Variable Declarators and Types: multiple declarators in one
// declaration, declarators with and without initializers, and the C-style array
// declarator where the brackets follow the variable name.
class LocalVariableDeclarators_4 {
    static void run() {

        // A declarator may carry extra bracket pairs after its name (C-style),
        // giving it array type even though the leading type is a scalar.
        int scalar = 7, arr[] = {10, 20, 30};
        check(arr[2] == 30);
    }
}
