// An int[] element read in a specification keeps its int range, so it can be
// passed to a @Transparent function's int parameter: in a postcondition, a
// precondition, a loop invariant, a quantifier body and a throwsOn
// postcondition. The index bound is
// still an obligation: each failing method reads an element its contract does
// not bound and fails exactly once.
package org.strata.jverify.verifier.tests.javasupport.arrays;
import org.strata.jverify.testengine.JVerifyTest;
import org.strata.jverify.*;
import static org.strata.jverify.JVerify.*;
@JVerifyTest(exitCode = 4, methodsInvalid = 5, errorCount = 5, methodsVerified = 8, methodsSkipped = 0)
class ArrayElementsInSpecs {
    @Transparent
    static boolean lower(int c) {
        return c >= 97 && c <= 122;
    }

    static boolean inPostcondition(int[] s, int k) {
        precondition(0 <= k && k < s.length);
        postcondition((boolean r) -> r == lower(s[k]));
        return s[k] >= 97 && s[k] <= 122;
    }

    static void inPrecondition(int[] s, int k) {
        precondition(0 <= k && k < s.length && lower(s[k]));
        check(s[k] >= 97);
    }

    static int inInvariant(int[] s) {
        int n = 0;
        for (int i = 0; i < s.length; i++) {
            invariant(0 <= i && i <= s.length && 0 <= n && n <= i);
            invariant(i == 0 || lower(s[i - 1]) || n <= i);
            if (lower(s[i])) {
                n++;
            }
        }
        return n;
    }

    static boolean inQuantifier(int[] s) {
        precondition(forall((int i) -> !(0 <= i && i < s.length) || lower(s[i])));
        return true;
    }

    static class E extends Exception {}
//         ^ warning: missing contract for method '<init>' in class 'java.lang.Exception'

    static int inThrowsOn(int[] s, int k) throws E {
        precondition(0 <= k && k < s.length && lower(s[k]));
        throwsOn(k == 0).postcondition((E e) -> lower(s[k]));
        if (k == 0) {
            throw new E();
        }
        return 0;
    }

    static boolean postconditionUnbounded(int[] s, int k) {
        precondition(0 <= k);
        postcondition((boolean r) -> !r || lower(s[k]));
//                                   ^^^^^^^^^^^^^^^^^^ Error: precondition does not hold
        return false;
    }

    static void preconditionBoundTooLate(int[] s, int k) {
        precondition(lower(s[k]) && 0 <= k && k < s.length);
//      ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ Error: precondition does not hold
    }

    static int invariantUnbounded(int[] s) {
        int n = 0;
        for (int i = 0; i < s.length; i++) {
            invariant(0 <= i && i <= s.length && 0 <= n && n <= i);
            invariant(lower(s[i]) || n <= i);
//          ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ Error: precondition does not hold
        }
        return n;
    }

    static boolean quantifierUnbounded(int[] s) {
        precondition(forall((int i) -> 0 <= i || lower(s[i])));
//                                     ^^^^^^^^^^^^^^^^^^^^^ Error: precondition does not hold
        return true;
    }

    static boolean postconditionWrong(int[] s, int k) {
        precondition(0 <= k && k < s.length);
        postcondition((boolean r) -> r == lower(s[k]));
//                                   ^^^^^^^^^^^^^^^^^ Error: postcondition does not hold
        return s[k] >= 97;
    }
}
