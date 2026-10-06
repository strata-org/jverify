package org.strata.jverify.langref.jls8.s8_4;

import static org.strata.jverify.JVerify.*;

// JLS 8.4.7 -- Method Body: a concrete method body is a block of statements;
// a method declared with a semicolon (no body) is abstract or native. Here we
// exercise a block body with local variables, control flow, and a return.
class MethodBody_1 {
    // The block body contains declarations, a loop, a branch, and a return.
    static int factorial(int n) {
        int result = 1;
        for (int i = 2; i <= n; i++) {
            result *= i;
        }
        if (n < 0) {
            return 0;
        }
        return result;
    }

    static void run() {

        check(factorial(0) == 1);
    }
}
