package org.strata.jverify.verifier.tests.javasupport.expressions;

import org.strata.jverify.Unbounded;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * Overflow of int/long arithmetic is a proof obligation at the OPERATION,
 * not only at assignment / declaration / return / parameter positions
 * (the original soundness bug: {@code (a + b) / 2} verified although {@code a + b}
 * overflows, because only the division's result reached a checked
 * position).
 *
 * <p>Each {@code +}/{@code -}/{@code *}/unary {@code -} on int or long is
 * emitted as a call to a checked helper ({@code java?add_int32(a, b)})
 * whose {@code requires} bounds the mathematical result to the operand
 * width. The helper's body is transparent, so in specs the call's value is
 * the mathematical result; the obligation is checked wherever Strata
 * checks call preconditions — bodies, contracts, and conditionally
 * evaluated positions (guarded, like a division's divisor check).
 *
 * <p>Consequences covered here:
 * <ul>
 *   <li>Spec arithmetic is checked too, so a sum guard must itself be
 *       overflow-free: {@code a <= Integer.MAX_VALUE - b}, not
 *       {@code a + b <= Integer.MAX_VALUE}.</li>
 *   <li>{@code @Unbounded} operands opt out (mathematical integers), in
 *       bodies and in contracts.</li>
 *   <li>Division overflow ({@code Integer.MIN_VALUE / -1}) is NOT yet an
 *       obligation at the operation; it still surfaces only at checked
 *       positions. Tracked as a follow-up.</li>
 * </ul>
 */
@JVerifyTest(exitCode = 4, errorCount = 10, methodsVerified = 10)
class ArithmeticOverflow {

    // The ticket's reproducer: a + b overflows at a = 1, b = Integer.MAX_VALUE,
    // and Java then returns a negative average.
    static int wrongAverage(int a, int b) {
        precondition(0 <= a && 0 <= b);
        postcondition((int r) -> r >= 0);
        return (a + b) / 2;
//      ^^^^^^^^^^^^^^^^^^^ Error: no int overflow on '+' does not hold
    }

    // The overflow-free guard idiom. The body's sum and the postcondition's
    // both discharge off the precondition.
    static int average(int a, int b) {
        precondition(0 <= a && 0 <= b && a <= Integer.MAX_VALUE - b);
        postcondition((int r) -> r >= 0 && r <= a + b);
        return (a + b) / 2;
    }

    // Overflow in a spec with nothing to discharge it fails in the spec.
    static int specOverflows(int a, int b) {
        precondition(a * b > 0);
//      ^^^^^^^^^^^^^^^^^^^^^^^^ Error: no int overflow on '*' does not hold
        return 1;
    }

    static int subUnderflow(int a) {
        precondition(a < 0);
        return (a - Integer.MAX_VALUE) / 2;
//      ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ Error: no int overflow on '-' does not hold
    }

    static int negOverflow(int a) {
        return 1 + (-a) / 2;
//      ^^^^^^^^^^^^^^^^^^^^ Error: no int overflow on '-' does not hold
    }

    // The failed '*' obligation leaves the quotient unconstrained, so the
    // method's int64 result range (reported as a postcondition on the
    // method) cascades — same as a failed assignment check does today.
    static long mulLongOverflow(long a) {
//  ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ Error: postcondition does not hold
        precondition(a > 4000000000L);
        return (a * a) / 2L;
//      ^^^^^^^^^^^^^^^^^^^^ Error: assertion does not hold
//      ^^^^^^^^^^^^^^^^^^^^ Error: no long overflow on '*' does not hold
    }

    // int arithmetic widened to long before the op is 64-bit checked:
    // (long) a + (long) b cannot overflow a long.
    static long averageViaLong(int a, int b) {
        return ((long) a + (long) b) / 2L;
    }

    // @Unbounded operands opt out: their arithmetic is mathematical, in the
    // body and in contracts (the captured parameters' annotations reach the
    // spec lambda).
    static @Unbounded int unboundedSum(@Unbounded int a, @Unbounded int b) {
        postcondition((@Unbounded int r) -> r == a + b);
        return a + b;
    }

    // Conditionally evaluated arithmetic keeps its guard: the obligation is
    // path-sensitive, like a guarded division's divisor check.
    static boolean guardedByAnd(int a) {
        precondition(a >= 0);
        return a <= 10000 && a * a < Integer.MAX_VALUE;
    }

    static int guardedByTernary(int a) {
        precondition(a >= 0);
        return a <= 10000 ? a * a : 0;
    }

    // A loop guard runs inside the loop's block on every iteration, so its
    // arithmetic is checked on every iteration: with no upper bound on i,
    // the guard's i + 1 can overflow and the obligation fails. The unproved
    // guard leaves i's post-loop range unknown, so the int32 range of the
    // returned value (the assertion at the return, and the implicit
    // postcondition) cascades to unproved too.
    static int runawayGuard(int n) {
//  ^ Error: postcondition could not be proved
        precondition(0 <= n);
        int i = 0;
        while (i + 1 <= n) {
//      ^^^^^^^^^^^^^^^^^^^^ Error: no int overflow on '+' could not be proved
            invariant(0 <= i);
            i = i + 1;
        }
        return i;
//      ^^^^^^^^^ Error: assertion could not be proved
    }

    // An @Unbounded-returning call's result stays mathematical: without the
    // return-annotation propagation, big(x) + big(x) would carry an int32
    // obligation no proof can discharge.
    static @Unbounded int big(@Unbounded int x) {
        return x;
    }

    static @Unbounded int sumOfBig(@Unbounded int x) {
        return big(x) + big(x);
    }

    // Capture plumbing in spec lambdas: the @Unbounded capture's type must
    // land on the right slot after a 2-slot long capture ...
    static @Unbounded int strideCapture(long a, @Unbounded int u) {
        precondition(0 <= a && a <= 10);
        postcondition((@Unbounded int r) -> a >= 0 && r == u + u);
        return u + u;
    }

    // ... and after the receiver of an instance (this-capturing) impl.
    @Unbounded int acc;

    @Unbounded int instanceCapture(@Unbounded int u) {
        postcondition((@Unbounded int r) -> r == acc + u);
        return acc + u;
    }
}
