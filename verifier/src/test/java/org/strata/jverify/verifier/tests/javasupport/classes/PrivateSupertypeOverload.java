package org.strata.jverify.verifier.tests.javasupport.classes;

import org.strata.jverify.Pure;
import org.strata.jverify.Unbounded;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * A {@code private} method of a STRICT supertype must not count toward the
 * ancestry overload check: it is not a member of the receiver's type (JLS 8.2),
 * so it is unreachable through such a receiver.
 *
 * <p>{@code BaseR.val(String)} is private, {@code SubR.val(int)} is not. Two
 * same-named declarations with different signatures in the closure, one
 * selectable: javac rejects {@code s.val(...)} resolving to the private one, and
 * Strata's {@code #val} resolves to the most-specific DECLARER, which the private
 * method's class cannot be while a nearer class declares the name. One candidate,
 * no ambiguity — counting the private declaration would refuse a provably-safe
 * call for no soundness gain.
 *
 * <p>Scoped to STRICT supertypes deliberately: on the receiver's OWN class a
 * private method IS selectable and must keep counting — a same-class
 * private/non-private overload pair is a real collision, caught at the
 * declaration site, which applies the per-class check regardless of visibility.
 *
 * <p>Verified 6 = both {@code val} bodies + {@code root} + implicit constructors
 * for the three classes. Skipped 0. Count the private declaration again and
 * {@code root} flips to Skipped, verified drops to 5.
 */
@JVerifyTest(methodsVerified = 6, methodsSkipped = 0, errorCount = 0)
public class PrivateSupertypeOverload {

    static class BaseR {
        @Pure @Unbounded private int val(String s) { return 0; }
    }

    static final class SubR extends BaseR {
        @Pure @Unbounded int val(int x) { postcondition((int r) -> r == 9); return 9; }
    }

    static void root(SubR s) {
        // Only SubR.val(int) is reachable; the private BaseR.val(String) is not
        // a member of SubR, so `#val` has nothing to be ambiguous with.
        check(s.val(1) == 9);
    }
}
