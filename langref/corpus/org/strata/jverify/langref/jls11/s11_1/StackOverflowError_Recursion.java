package org.strata.jverify.langref.jls11.s11_1;

import static org.strata.jverify.JVerify.*;

// JLS 11.1.2 -- StackOverflowError from unbounded recursion. The JVM raises
// StackOverflowError, not Error, so we catch it directly.
class StackOverflowError_Recursion {
    static int recurse(int n) {
        return recurse(n + 1);
    }

    static void run() {
        boolean caught = false;
        try {
            recurse(0);
        } catch (StackOverflowError e) {
            caught = true;
        }
        check(caught);
    }
}
