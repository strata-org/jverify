package org.strata.jverify.langref.jls14.s14_4;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 14.4.2 -- Local Variable Declaration Statements (incl var): a local variable
// declaration as a statement, the final modifier, and local variable type
// inference via the reserved type name var.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls14.s14_4.LocalVariableDeclarationStatements_2#run")
class LocalVariableDeclarationStatements_2 {
    static void run() {

        // A final local variable: its value cannot change after initialization.
        final int limit = 100;
        check(limit == 100);
    }
}
