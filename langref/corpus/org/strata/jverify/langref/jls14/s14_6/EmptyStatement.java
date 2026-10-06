package org.strata.jverify.langref.jls14.s14_6;

import static org.strata.jverify.JVerify.*;

// JLS 14.6 -- The Empty Statement: a lone ';' does nothing and completes normally.
class EmptyStatement {
    static void run() {
        int x = 2 + 3;
        ;                       // empty statement: does nothing
        ;;                      // two empty statements in a row
        for (int i = 0; i < 3; i++)
            ;                   // empty statement as a loop body
        if (x == 5)
            ;                   // empty statement as the 'then' branch
        check(x == 5);
    }
}
