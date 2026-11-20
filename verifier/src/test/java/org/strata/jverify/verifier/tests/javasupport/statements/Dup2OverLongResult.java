package org.strata.jverify.verifier.tests.javasupport.statements;

import org.strata.jverify.Transparent;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(exitCode = 0, methodsVerified = 4)
class Dup2OverLongResult {
    @Transparent
    static int foo(int k, long v) { return k; }
    static void quotient(long x) { long t; int r = foo(7, t = x / 3); check(r == 7); }
    static void remainder(long x) { long t; int r = foo(7, t = x % 3); check(r == 7); }
}
