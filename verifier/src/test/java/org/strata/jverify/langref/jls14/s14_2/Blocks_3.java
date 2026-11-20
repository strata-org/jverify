package org.strata.jverify.langref.jls14.s14_2;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 14.2 -- Blocks: a block is a sequence of statements, local class/interface
// declarations, and local variable declarations within braces; it is executed by
// executing each block statement in order, and introduces a nested scope.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls14.s14_2.Blocks_3#run")
class Blocks_3 {
    static void run() {

        int sum = 0;

        // A block as its own statement, introducing a nested scope.
        {
            int x = 10;            // local variable scoped to this block
            int y = 20;
            sum = x + y;           // statements executed in order
        }

        // A nested block: a variable declared in the outer block is visible
        // in the inner block; a variable declared in the inner block is not
        // visible after the inner block ends.
        {
            int outer = 5;
            {
                int inner = 7;
                outer = outer + inner;   // inner block sees 'outer'
            }
            // 'inner' is out of scope here
            sum = outer;
        }

        // A new block may reuse a name once a previous block's scope has ended.
        {
            int t = 100;
            sum = t;
        }
        check(sum == 100);
    }
}
