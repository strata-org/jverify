package org.strata.jverify.verifier.tests.javasupport.statements;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * A block-local variable's scope ends with its block, so a sibling block may
 * reuse the same name for a distinct variable. The frontend emits an ordinary
 * (unlabeled) Laurel block; scoping is guaranteed by the Laurel lowering, which
 * gives every block its own scoped Core block, so the frontend needs no special
 * handling. Without that per-block scoping the second declaration would collide
 * ("Variable t already in context").
 */
@JVerifyTest(methodsVerified = 3, errorCount = 0)
class BlockScopeReuse {
    static void siblingBlocksReuseName() {
        {
            int t = 100;
            check(t == 100);
        }
        {
            int t = 200;           // distinct variable; previous 't' is out of scope
            check(t == 200);
        }
    }

    static void nestedBlockShadowsThenReuses() {
        int t = 1;
        {
            int u = t + 1;         // reads the outer t
            check(u == 2);
        }
        {
            int u = t + 10;        // reuse 'u' in a sibling block
            check(u == 11);
        }
        check(t == 1);
    }
}
