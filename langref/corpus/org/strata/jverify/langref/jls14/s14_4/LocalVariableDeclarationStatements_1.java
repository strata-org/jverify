package org.strata.jverify.langref.jls14.s14_4;

import static org.strata.jverify.JVerify.*;

// JLS 14.4.2 -- Local Variable Declaration Statements (incl var): a local variable
// declaration as a statement, the final modifier, and local variable type
// inference via the reserved type name var.
class LocalVariableDeclarationStatements_1 {
    static void run() {

        // A plain local variable declaration statement.
        int count = 0;
        count = count + 5;
        check(count == 5);
    }
}
