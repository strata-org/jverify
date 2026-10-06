package org.strata.jverify.langref.jls14.s14_4;

import static org.strata.jverify.JVerify.*;

// JLS 14.4.2 -- Local Variable Declaration Statements (incl var): a local variable
// declaration as a statement, the final modifier, and local variable type
// inference via the reserved type name var.
class LocalVariableDeclarationStatements_6 {
    static void run() {

        // var inferred as an array type from an array creation expression.
        var data = new int[]{2, 4, 6};
        check(data[1] == 4);
    }
}
