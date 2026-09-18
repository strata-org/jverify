package org.strata.jverify.verifier.tests.javasupport;

import org.strata.jverify.Unbounded;
import org.strata.jverify.Verify;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * An overload opted out via {@code @Verify(false)} emits no procedure, so it
 * cannot collide with its same-name sibling — the sibling must still verify.
 * Regression: refuseIfOverloaded counted opted-out methods, so adding a
 * {@code @Verify(false)} overload silently disabled verification of every
 * method sharing its name. Covers the method-level opt-out and the class-level
 * one with a method opting back in.
 * Verified 3: foo(int) + this class's implicit constructor + bar(int).
 * Skipped 3: the two opted-out overloads, plus the companion class's implicit
 * constructor (it inherits the class-level opt-out).
 */
@JVerifyTest(methodsVerified = 3, methodsSkipped = 3, errorCount = 0,
        additionalFiles = {"./VerifyFalseOverloadClassLevel.java"})
final class VerifyFalseOverload {

    @Verify(false)
    static int foo(@Unbounded int x, @Unbounded int y) {
        return x + y;
    }

    @Unbounded
    static int foo(@Unbounded int x) {
        check(x == x);
        return x;
    }
}
