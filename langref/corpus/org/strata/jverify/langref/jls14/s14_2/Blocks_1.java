package org.strata.jverify.langref.jls14.s14_2;

import static org.strata.jverify.JVerify.*;

// JLS 14.2 -- Blocks: a block is a sequence of statements, local class/interface
// declarations, and local variable declarations within braces; it is executed by
// executing each block statement in order, and introduces a nested scope.
class Blocks_1 {
    static void run() {

        int sum = 0;

        // A block as its own statement, introducing a nested scope.
        {
            int x = 10;            // local variable scoped to this block
            int y = 20;
            sum = x + y;           // statements executed in order
        }
        check(sum == 30);
    }
}
