package org.strata.jverify.langref.jls6.s6_3;

import static org.strata.jverify.JVerify.*;

// JLS 6.3 -- Scope: a local variable declared inside a nested block does not
// affect enclosing scopes; the outer `x` retains its value once the inner
// block ends, since `y` goes out of scope at the block's close.
class Scope_3 {
    static void run() {
        int x = 1;
        {
            int y = 2;
            // y is only in scope inside this block
            y = y + 1;
        }
        check(x == 1);
    }
}
