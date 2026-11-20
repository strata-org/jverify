package org.strata.jverify.verifier.tests.javasupport.classes;

import org.strata.jverify.Transparent;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * Soundness regression for polymorphic instance calls, now resting entirely on
 * Strata's override-refinement (Liskov) checking rather than on a front-end
 * refusal.
 *
 * <p>{@code SubP} overrides {@code BaseP.compute} (postcondition {@code r >= 0})
 * with an empty contract and a body returning {@code -1}. Two independent
 * mechanisms reject this program, and BOTH must keep firing:
 *
 * <ul>
 * <li>Strata's {@code CheckOverrideRefinement} pass checks that
 * {@code SubP.compute} refines {@code BaseP.compute}. {@code SubP}'s empty
 * declared postcondition guarantees nothing, so the parent's {@code r >= 0}
 * cannot be re-established over the override's result — a genuine Liskov
 * violation, reported as "override postcondition no weaker than '$result >= 0' from
 * '...BaseP.compute' could not be proved" and anchored at {@code SubP.compute} -- the
 * violation is the override's defect, so that is where it is reported.
 * <li>The call site {@code check(b.compute() >= 0)} in {@code root} cannot be proved.
 * {@code b} is {@code BaseP}-typed, so the caller may assume only {@code BaseP}'s
 * contract while any override may run -- and dispatch is dynamic, so the override that
 * runs is {@code SubP}'s, which guarantees nothing. Measured: give {@code SubP} a
 * contract that genuinely refines ({@code r >= 5}) and this same call site verifies.
 * </ul>
 *
 * <p>No front-end refusal of polymorphic calls is needed for soundness: Laurel
 * dispatches an overridden composite method on the receiver's
 * runtime tag ({@code LiftInstanceProcedures}) and {@code CheckOverrideRefinement}
 * discharges the obligation that makes reasoning against the declared type's contract
 * sound, so {@code root} is translated rather
 * than skipped.
 *
 * <p>The Liskov failure is anchored at the override: the procedure's name identifier
 * carries the method's range.
 *
 * <p>Counts: the three implicit constructors and {@code BaseP.compute} verify;
 * {@code SubP.compute} fails its refinement obligation and {@code root} fails its
 * assertion.
 */
@JVerifyTest(continueOnErrors = true, exitCode = 4, methodsVerified = 4, errorCount = 2)
class PolyDispatchSoundness {
    static class BaseP {
        int compute() { postcondition((int r) -> r >= 0); return 0; }
    }
    static class SubP extends BaseP {
        @Transparent @Override int compute() { return -1; }
//      ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ Error: override postcondition no weaker than '$result >= 0' from 'org.strata.jverify.verifier.tests.javasupport.classes.PolyDispatchSoundness.BaseP.compute' does not hold
    }
    static void root() {
        BaseP b = new SubP();
        check(b.compute() >= 0);
//      ^^^^^^^^^^^^^^^^^^^^^^^ Error: assertion does not hold
    }
}
