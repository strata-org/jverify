package org.strata.jverify.verifier.tests.javasupport.classes;

import org.strata.jverify.Pure;
import org.strata.jverify.Unbounded;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * The cost of the ancestry overload check: overloading the name of an INHERITED
 * method refuses the call, even when the declaration comes from a library type
 * the user never wrote.
 *
 * <p>{@code Object} declares {@code toString()}; {@code Holder} adds
 * {@code toString(int)}. Two erased signatures across the receiver's supertype
 * closure, so {@code refuseIfOverloadedAcrossAncestry} refuses. Not a false
 * positive: the emitted {@code h#toString} names only the method, so by-name
 * resolution cannot tell them apart and could bind the wrong contract.
 *
 * <p>Blast radius grows with builtin-contract coverage: any user method
 * overloading an inherited {@code java.lang} / {@code java.util} name is
 * refused, and those are overload-dense. A merely-inherited name is NOT refused
 * -- a uniquely-named method on {@code Object}-only ancestry verifies normally,
 * since the check filters by name before counting signatures.
 *
 * <p>By-signature resolution in Strata removes this cost; this test should then
 * flip to expecting a clean verify.
 *
 * <p>Verified 3 = {@code toString(int)} + implicit constructors for
 * {@code InheritedNameOverload} and {@code Holder}. Skipped 1 = {@code root}.
 */
@JVerifyTest(continueOnErrors = true, exitCode = 0, methodsVerified = 3, methodsSkipped = 1, errorCount = 0)
public class InheritedNameOverload {

    static final class Holder {
        @Pure @Unbounded int toString(int x) { postcondition((int r) -> r == 5); return 5; }
    }

    static void root(Holder h) {
//              ^ error: overloaded method 'toString' (2 signatures visible on 'org.strata.jverify.verifier.tests.javasupport.classes.InheritedNameOverload.Holder' and its supertypes); overloading is not supported
        check(h.toString(1) == 5);
    }
}
