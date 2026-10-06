package org.strata.jverify.langref.jls14.s14_4;

import static org.strata.jverify.JVerify.*;

// JLS 14.4.2 -- Local Variable Declaration Statements (incl var): a local variable
// declaration as a statement, the final modifier, and local variable type
// inference via the reserved type name var.
class LocalVariableDeclarationStatements_4 {
    static void run() {

        // A final local variable: its value cannot change after initialization.
        final int limit = 100;

        // var inferred as long, then used in arithmetic.
        var inferredLong = 10L;
        long total = inferredLong + limit;
        check(total == 110L);
    }
}
