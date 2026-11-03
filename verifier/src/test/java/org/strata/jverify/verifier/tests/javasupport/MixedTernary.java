package org.strata.jverify.verifier.tests.javasupport;

import static org.strata.jverify.JVerify.*;

import org.strata.jverify.testengine.JVerifyTest;

@JVerifyTest(exitCode = 4, methodsVerified = 8, errorCount = 1)
class MixedTernary {

    static int f() {
        postcondition((int r) -> r == 3);
        return 3;
    }

    static int callThenLiteral(boolean c) {
        postcondition((int r) -> r == (c ? 3 : 0));
        return c ? f() : 0;
    }

    static int literalThenCall(boolean c) {
        postcondition((int r) -> r == (c ? 0 : 3));
        return c ? 0 : f();
    }

    static int inc(int x) {
        return x < Integer.MAX_VALUE ? x + 1 : f();
    }

    static int dec(int x) {
        return x == Integer.MIN_VALUE ? f() : x - 1;
    }

    static int same(int x) {
        postcondition((int r) -> r == x);
        return x;
    }

    static void invariantWithATernary(int n) {
        precondition(0 <= n && n < 1000);
        int i = 0;
        while (i < n) {
            invariant(0 <= i && i <= n);
            invariant(i >= 0 || (n > 5 ? same(n) : same(n)) > 0);
            i = i + 1;
        }
        check(i == n);
    }

    static void wrongValue() {
        check(callThenLiteral(true) == 0);
//      ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ Error: assertion does not hold
    }
}
