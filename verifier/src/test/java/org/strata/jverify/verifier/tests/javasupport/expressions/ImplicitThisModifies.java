package org.strata.jverify.verifier.tests.javasupport.expressions;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * An instance method that writes a field through implicit {@code this} gets the
 * synthesized {@code modifies} wildcard, and its frame check passes.
 *
 * <p>Inside an instance method a bare {@code f = ...} is an implicit
 * {@code this.f = ...} and reaches javac as a {@code JCIdent}. The heap-write scan
 * matched only the qualified {@code JCFieldAccess} form, so no wildcard was synthesized
 * and Strata's frame check failed on a write the frame should have covered. Measured:
 * `this.f = 7` verified while the equivalent `f = 7` did not.
 *
 * <p>The diagnostic was also invisible. Strata takes the frame condition's location
 * from the procedure's name identifier ({@code ModifiesClauses.buildQuantifiedFrame}
 * reads {@code proc.name.source}), which jverify emitted range-less, so the error
 * landed on {@code <unknown>(1:1)}, was dropped from the per-method counts, and the
 * method was reported Verified. Both halves are fixed; this fixture pins the outcome.
 */
@JVerifyTest(methodsVerified = 5, errorCount = 0)
class ImplicitThisModifies {
    static class Cell {
        int f;

        /** Implicit this: the bare `f` on both sides. */
        final void writeImplicit() {
            f = 7;
            check(f == 7);
        }

        /** Explicit this: verified before the fix too, kept as the control. */
        final void writeExplicit() {
            this.f = 8;
            check(this.f == 8);
        }
    }

    static void callThem(Cell c) {
        c.writeImplicit();
        c.writeExplicit();
    }
}
