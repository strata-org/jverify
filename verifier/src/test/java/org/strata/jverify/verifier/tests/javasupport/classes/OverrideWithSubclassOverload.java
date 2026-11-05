package org.strata.jverify.verifier.tests.javasupport.classes;

import org.strata.jverify.Transparent;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * Soundness regression for OVERRIDE-STABLE overload naming, from a case raised in
 * review: an override whose subclass ALSO declares an unrelated same-named
 * overload must still be a Laurel override of its base.
 *
 * <p>This is {@link PolyDispatchSoundness} plus one extra declaration —
 * {@code SubQ.m(String)}, a subclass-added overload that the base knows nothing
 * about. The overload suffix used to be measured over the DECLARING class's type
 * closure, which made it disagree along the override chain: closure({@code BaseQ})
 * sees one signature for {@code m} so {@code BaseQ.m(int)} emitted a bare
 * {@code m}, while closure({@code SubQ}) sees two so {@code SubQ.m(int)} emitted
 * {@code m$int}. Different names mean Strata's {@code CheckOverrideRefinement}
 * never pairs them, so the Liskov obligation that the removal of the front end's
 * {@code dispatchRefusal} relies on was never generated — and the call site,
 * resolving the bare {@code m} to {@code BaseQ}'s body, was proved. This exact
 * program reported 0 errors and 7 verified methods while Java runs
 * {@code SubQ.m(int)} and returns {@code -1}.
 *
 * <p>The suffix is now measured over the closure of the override chain's ROOT
 * declaration, so base and override agree by construction. Both defences of
 * {@link PolyDispatchSoundness} are therefore back, and BOTH must keep firing:
 * the Liskov violation on {@code BaseQ.m}'s postcondition, and the unprovable
 * {@code check} at the polymorphic call site. Either diagnostic going missing
 * means the naming has gone back to being closure-relative.
 *
 * <p>The subclass-added overload must at the same time keep a name DISTINCT from
 * its sibling on {@code SubQ} — Laurel has no overloading for composite instance
 * methods, so a collision here fails translation outright with "Duplicate
 * definition". {@code SubQ.m(String)} is its own root, whose closure sees both
 * signatures, so it is emitted as {@code m$java.lang.String}.
 *
 * <p>Counts: the three implicit constructors, {@code SubQ.m(int)} and
 * {@code SubQ.m(String)} verify; {@code BaseQ.m(int)} fails its refinement
 * obligation and {@code root} fails its assertion.
 */
@JVerifyTest(continueOnErrors = true, exitCode = 4, methodsVerified = 5, errorCount = 2)
class OverrideWithSubclassOverload {
    static class BaseQ {
        int m(int x) { postcondition((int r) -> r >= 0); return 1; }
    }
    static class SubQ extends BaseQ {
        @Transparent @Override int m(int x) { return -1; }
//      ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ Error: override postcondition no weaker than '$result >= 0' from 'org.strata.jverify.verifier.tests.javasupport.classes.OverrideWithSubclassOverload.BaseQ.m' does not hold
        // Not an override of anything: a subclass-added overload, and the reason
        // the closure-relative suffix diverged from the base's.
        @Transparent int m(String s) { return 2; }
    }
    static void root() {
        BaseQ b = new SubQ();
        check(b.m(1) >= 0);
//      ^^^^^^^^^^^^^^^^^^ Error: assertion does not hold
    }
}
