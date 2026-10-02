package org.strata.jverify.verifier.tests.javasupport.expressions;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * An {@code instanceof} test followed by a hand-written cast is ordinary code and
 * must translate, even though it compiles to exactly the instructions of a pattern
 * binding ({@code i instanceof C c}): LOAD, INSTANCEOF, IFEQ, then LOAD, CHECKCAST,
 * STORE. A frontend that recognises a pattern binding by instruction shape alone
 * refuses these methods as if they were one, and each reports a warning instead of
 * verifying. The two forms differ only in where the source statements begin, which
 * is what a bytecode frontend has to read.
 *
 * <p>{@code sameObject} proves the cast yields the tested object itself.
 * {@code twin} is its must-fail control: the branch is reachable, so an assertion
 * that cannot hold there must be reported -- otherwise {@code sameObject} could be
 * passing because its branch was translated as dead. No field is read, so neither
 * verdict depends on how field ranges are axiomatised.
 *
 * <p>Verified 3 = {@code sameObject} + the implicit constructors of the fixture and
 * C; {@code twin} fails its assertion.
 */
@JVerifyTest(exitCode = 4, methodsVerified = 3, errorCount = 1)
class ManualCastAfterInstanceof {
    interface I {}

    static class C implements I {}

    static void sameObject(I i) {
        if (i instanceof C) {
            C c = (C) i;
            check(c == i);
        }
    }

    static void twin(I i) {
        if (i instanceof C) {
            C c = (C) i;
            check(c != i);
//          ^^^^^^^^^^^^^ Error: assertion could not be proved
        }
    }
}
