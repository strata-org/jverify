package org.strata.jverify.langref.jls14.s14_4;

import static org.strata.jverify.JVerify.*;

// JLS 14.4.2 -- Local Variable Declaration Statements (incl var): a local variable
// declaration as a statement, the final modifier, and local variable type
// inference via the reserved type name var.
class LocalVariableDeclarationStatements_3 {
    static void run() {

        // var: the type is inferred from the initializer. Inferred as int.
        var inferredInt = 42;
        check(inferredInt == 42);
    }
}
