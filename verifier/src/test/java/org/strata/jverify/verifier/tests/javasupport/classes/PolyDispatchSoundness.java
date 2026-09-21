package org.strata.jverify.verifier.tests.javasupport.classes;

import org.strata.jverify.Pure;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * Soundness regression for the monomorphism gate on instance calls, now also
 * exercising Strata's override-refinement (Liskov) checking.
 *
 * <p>{@code SubP} overrides {@code BaseP.compute} (postcondition {@code r >= 0})
 * with an empty contract and a body returning {@code -1}. Two independent
 * soundness mechanisms fire on this program:
 *
 * <ul>
 * <li>The Java front-end refuses the call {@code b.compute()} in {@code root}
 * because it is not provably monomorphic (neither callee/class final nor callee
 * private): a graceful skip with the "polymorphic dispatch … not yet supported"
 * diagnostic. Without it, Laurel would resolve {@code obj#method} against the
 * receiver's DECLARED type and route to {@code BaseP}'s {@code r >= 0} contract
 * even though {@code b} holds a {@code SubP} returning {@code -1}.
 * <li>Strata's {@code CheckOverrideRefinement} pass now checks that
 * {@code SubP.compute} refines {@code BaseP.compute}. {@code SubP}'s empty
 * declared postcondition guarantees nothing, so the parent's {@code r >= 0}
 * cannot be re-established over the override's result — a genuine Liskov
 * violation, reported as "override postcondition no weaker than '$result >= 0' from
 * '...BaseP.compute' could not be proved" and anchored at {@code SubP.compute} —
 * the violation is the override's defect, so that is where it is reported. (This replaces
 * the earlier behaviour where {@code SubP.compute} verified vacuously against its empty
 * contract.)
 * </ul>
 *
 * <p>So {@code SubP.compute} is the method that fails, and {@code BaseP.compute} one of the
 * four that verify alongside the three implicit constructors; the unsound call site is the
 * skip. The bytecode frontend reports the same message on the inherited clause instead, and
 * carries an XFAIL in {@code bytecode-xfails.txt} saying why.
 *
 * <p>Re-enable verification of the {@code root} call when runtime dispatch lands
 * (Strata#1174) — at which point the front-end's blanket refusal can be relaxed,
 * since Strata now soundly Liskov-checks the override.
 */
@JVerifyTest(continueOnErrors = true, exitCode = 4, methodsVerified = 4, methodsSkipped = 1, errorCount = 1)
class PolyDispatchSoundness {
    static class BaseP {
        @Pure int compute() { postcondition((int r) -> r >= 0); return 0; }
    }
    static class SubP extends BaseP {
        @Pure @Override int compute() { return -1; }
//      ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ Error: override postcondition no weaker than '$result >= 0' from 'org.strata.jverify.verifier.tests.javasupport.classes.PolyDispatchSoundness.BaseP.compute' could not be proved
    }
    static void root() {
//              ^ error: polymorphic dispatch through interface/superclass is not yet supported
        BaseP b = new SubP();
        check(b.compute() >= 0);
    }
}
