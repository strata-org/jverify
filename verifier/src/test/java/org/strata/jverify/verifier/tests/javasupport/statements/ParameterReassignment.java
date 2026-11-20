package org.strata.jverify.verifier.tests.javasupport.statements;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * Assigning to a formal parameter is legal Java, and Laurel allows body writes
 * to procedure inputs — but Core does not (a procedure may only write its
 * outputs and body-declared locals). Strata's {@code CopyMutatedInputs} pass
 * legalizes this by copying each mutated input into a fresh local and
 * redirecting the body to it. Before that pass existed, any method assigning
 * to a parameter failed Core type checking ("This procedure modifies variables
 * it is not allowed to") and the single error aborted every method in the
 * program. Regression test for that whole-program abort.
 *
 * <p>The lambda contract overloads (e.g. {@code postcondition(Predicate)})
 * cannot capture a reassigned parameter — javac's effectively-final rule — but
 * the plain boolean overloads ({@code precondition(boolean)},
 * {@code postcondition(boolean)}) can mention one freely, as
 * {@code compoundReassign} and {@code instanceReassign} below do. Such a spec
 * reference denotes the parameter's ENTRY value (Strata leaves specifications
 * on the original, immutable input). Loop invariants are body code and track
 * the parameter's current (mutated) value; {@code loopCountdown} pins that.
 */
@JVerifyTest(methodsVerified = 5, errorCount = 0)
class ParameterReassignment {
    static void plainReassign(int x) {
        x = 1;
        check(x == 1);
    }

    static void compoundReassign(int x) {
        precondition(0 <= x && x < 1000);
        int entry = x;
        x += 1;
        check(x == entry + 1);
    }

    static void loopCountdown(int n) {
        precondition(0 <= n && n <= 100);
        int steps = 0;
        while (n > 0) {
            // The coupling bounds both variables, so the loop body's overflow
            // obligations discharge; `0 <= n` plus the exit condition give the
            // final check. The invariant mentions the reassigned parameter, so
            // it must track its current value, not its entry value.
            // Overflow-free form: `steps + n <= 100` would carry an overflow
            // obligation its own bound cannot discharge under havoc, and the
            // explicit n <= 100 replaces the int32 range the havoc drops.
            invariant(0 <= n && n <= 100 && 0 <= steps && steps <= 100 - n);
            steps = steps + 1;
            n = n - 1;
        }
        check(n == 0);
    }

    int instanceReassign(int x) {
        precondition(0 <= x && x < 1000);
        x = x + 1;
        check(x >= 1);
        return x;
    }
}
