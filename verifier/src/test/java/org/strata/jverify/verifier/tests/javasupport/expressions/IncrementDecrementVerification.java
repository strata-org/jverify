package org.strata.jverify.verifier.tests.javasupport.expressions;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.check;

/**
 * Tests for prefix and postfix increment/decrement operators in various expression positions.
 */
@SuppressWarnings("ConstantValue")
@JVerifyTest(methodsVerified = 4, errorCount = 0)
class IncrementDecrementVerification {

    static int identity(int x) {
        return x;
    }

    static int add(int a, int b) {
        return a + b;
    }

    /** Postfix ++ as argument to a call passes the old value. */
    static void postfixInCall() {
        var x = 5;
        var result = identity(x++);
        check(result == 5);
        check(x == 6);
    }

    /** Prefix ++ as argument to a call passes the new value. */
    static void prefixInCall() {
        var x = 5;
        var result = identity(++x);
        check(result == 6);
        check(x == 6);
    }

    /** Multiple increments in a single call. */
    static void multipleIncrementsInCall() {
        var a = 1;
        var b = 10;
        var result = add(a++, ++b);
        check(result == 12);
        check(a == 2);
        check(b == 11);
    }

    /** Prefix and postfix decrement in assignments. */
    static void decrementInAssignment() {
        var x = 10;
        var postDec = x--;
        check(postDec == 10);
        check(x == 9);
        var preDec = --x;
        check(preDec == 8);
        check(x == 8);
    }
}
