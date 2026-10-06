package org.strata.jverify.langref.jls6.s6_3;

import static org.strata.jverify.JVerify.*;

// JLS 6.3 -- Scope of a Declaration: a local variable declared in a block is
// in scope from its declarator to the end of the enclosing block. Here the
// inner block's local `y` is used within that block alongside the outer `x`.
class Scope_1 {
    static void run() {
        int x = 1;
        {
            int y = 2;
            check(x + y == 3);
        }
    }
}
