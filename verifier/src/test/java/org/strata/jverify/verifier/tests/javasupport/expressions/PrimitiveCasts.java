package org.strata.jverify.verifier.tests.javasupport.expressions;

import org.strata.jverify.Nat;
import org.strata.jverify.Pure;
import org.strata.jverify.Unbounded;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * Java primitive casts. JVerify translates each cast into a call to a
 * synthesized helper procedure:
 *
 *   (T) x    →    $cast_to_T_from_S(x)
 *
 * where S is the source type. The helper is emitted once per (S, T)
 * pair at file scope, with `requires` clauses expressing the target
 * type's range bounds inline and `opaque(ensures $result == x)`. The
 * call's precondition becomes a proof obligation at the cast site;
 * for widening it's trivially satisfied, for narrowing it's the real
 * range check. This mirrors how JVerify handles in-type overflow.
 * See docs/jccast-design.md.
 *
 * The function-call design gives uniform behavior in method bodies
 * and inside `precondition(...)` / `postcondition(...)` — Strata
 * checks a call's precondition wherever the call appears.
 *
 * The suite covers:
 *   - Every legal widening pair (verifies clean).
 *   - Every legal narrowing pair, in-range (verifies clean).
 *   - Every legal narrowing pair, out-of-range (fails with
 *     "cast from S to T does not hold" at the cast site).
 *   - Casts in various syntactic positions: variable initializer,
 *     method-call argument, if condition, precondition,
 *     postcondition, return, and nested inside binary/conditional
 *     expressions.
 */
@JVerifyTest(exitCode = 4, errorCount = 26, methodsVerified=42)
class PrimitiveCasts {

    // ============================================================
    // Widening (JLS §5.1.2). Value-preserving; range check trivial.
    // Order: byte -> short -> int -> long, plus char -> int/long.
    // ============================================================

    static void widenByteToShort(byte b) {
        short s = b;
        check(s == b);
    }

    static void widenByteToInt(byte b) {
        int i = b;
        check(i == b);
    }

    static void widenByteToLong(byte b) {
        long l = b;
        check(l == b);
    }

    static void widenShortToInt(short s) {
        int i = s;
        check(i == s);
    }

    static void widenShortToLong(short s) {
        long l = s;
        check(l == s);
    }

    static void widenIntToLong(int i) {
        long l = i;
        check(l == i);
    }

    static void widenCharToInt(char c) {
        int i = c;
        check(i >= 0);
        check(i <= 65535);
    }

    static void widenCharToLong(char c) {
        long l = c;
        check(l >= 0);
        check(l <= 65535);
    }

    // ============================================================
    // Narrowing in-range (JLS §5.1.3). Source known to fit target.
    // ============================================================

    static void narrowIntToByteInRange() {
        int i = 100;
        byte b = (byte) i;
        check(b == 100);
    }

    static void narrowIntToShortInRange() {
        int i = 100;
        short s = (short) i;
        check(s == 100);
    }

    static void narrowLongToIntInRange() {
        long l = 100L;
        int i = (int) l;
        check(i == 100);
    }

    static void narrowLongToShortInRange() {
        long l = 100L;
        short s = (short) l;
        check(s == 100);
    }

    static void narrowLongToByteInRange() {
        long l = 100L;
        byte b = (byte) l;
        check(b == 100);
    }

    static void narrowShortToByteInRange() {
        short s = 100;
        byte b = (byte) s;
        check(b == 100);
    }

    static void narrowIntToCharInRange() {
        int i = 65;
        char c = (char) i;
        check(c == 65);
    }

    static void narrowLongToCharInRange() {
        long l = 65L;
        char c = (char) l;
        check(c == 65);
    }

    // char is Java's only unsigned primitive, so cross-signedness casts
    // out of `char` are the corner of the widening/narrowing matrix that
    // most easily hides a bounds-encoding bug. Pin the two narrowing
    // targets from `char` at the boundary of each signed range.

    static void narrowCharToShortInRange() {
        char c = 'A';
        short s = (short) c;
        check(s == 65);
    }

    static void narrowCharToByteInRange() {
        char c = 'A';
        byte b = (byte) c;
        check(b == 65);
    }

    // The one legal narrowing INTO `char` we haven't covered.
    // byte -> char is JLS §5.5's "byte to char" narrowing (via
    // sign-extend + truncate); a NEGATIVE byte therefore fails the
    // `char` range check (`char in [0, 65535]`). Pinning this
    // deliberately: this is the exact spot where the "prove no
    // overflow" posture and JLS wrap semantics diverge, and someone
    // "fixing" the negative-byte case toward wrap must notice that
    // the whole design's coherence argument (docs/jccast-design.md)
    // hangs on it.

    static void narrowByteToCharNegative(byte b) {
        precondition(b < 0);
        char c = (char) b;
//      ^^^^^^^^^^^^^^^^^^ Error: cast from int8 to char does not hold
    }

    // ============================================================
    // Casts crossing the @Unbounded boundary. `@Unbounded int` is the
    // Laurel raw `int` (mathematically unbounded); the bounded majors
    // are int32/int16/int8/int64/char/etc. Widening from bounded to
    // unbounded is unconditionally sound; narrowing to bounded turns
    // the target's range into a proof obligation.
    // ============================================================

    static void widenIntToUnbounded(int x) {
        @Unbounded int y = (@Unbounded int) x;
        check(y == x);
    }

    static void narrowUnboundedToIntInRange(@Unbounded int x) {
        precondition(x >= -2147483648L && x <= 2147483647L);
        int i = (int) x;
        check(i == x);
    }

    static void narrowUnboundedToIntOutOfRange(@Unbounded int x) {
        precondition(x > 2147483647L);
        int i = (int) x;
//      ^^^^^^^^^^^^^^^^ Error: cast from int to int32 does not hold
    }

    // @Unbounded @Nat int has only a lower bound (x >= 0), so casting
    // to it from @Unbounded int only requires proving non-negativity.
    static void narrowUnboundedToNat(@Unbounded int x) {
        precondition(x >= 0);
        @Unbounded @Nat int n = (@Unbounded @Nat int) x;
        check(n == x);
    }

    static void narrowUnboundedToNatNegative(@Unbounded int x) {
        precondition(x < 0);
        @Unbounded @Nat int n = (@Unbounded @Nat int) x;
//      ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ Error: cast from int to nat does not hold
    }

    // ============================================================
    // Narrowing out-of-range (JLS §5.1.3). Fails at the cast site with
    // "cast from S to T does not hold" -- the label comes from the
    // ErrorSummary on the helper's requires clauses.
    // ============================================================

    static void narrowIntToByteOutOfRange() {
        int i = 300;
        byte b = (byte) i;
//      ^^^^^^^^^^^^^^^^^^ Error: cast from int32 to int8 does not hold
    }

    static void narrowIntToShortOutOfRange() {
        int i = 100000;
        short s = (short) i;
//      ^^^^^^^^^^^^^^^^^^^^ Error: cast from int32 to int16 does not hold
    }

    static void narrowLongToIntOutOfRange() {
        long l = 10_000_000_000L;
        int i = (int) l;
//      ^^^^^^^^^^^^^^^^ Error: cast from int64 to int32 does not hold
    }

    static void narrowLongToShortOutOfRange() {
        long l = 100000L;
        short s = (short) l;
//      ^^^^^^^^^^^^^^^^^^^^ Error: cast from int64 to int16 does not hold
    }

    static void narrowLongToByteOutOfRange() {
        long l = 300L;
        byte b = (byte) l;
//      ^^^^^^^^^^^^^^^^^^ Error: cast from int64 to int8 does not hold
    }

    static void narrowShortToByteOutOfRange() {
        short s = 300;
        byte b = (byte) s;
//      ^^^^^^^^^^^^^^^^^^ Error: cast from int16 to int8 does not hold
    }

    static void narrowIntToCharNegative() {
        int i = -1;
        char c = (char) i;
//      ^^^^^^^^^^^^^^^^^^ Error: cast from int32 to char does not hold
    }

    static void narrowCharToShortOutOfRange(char c) {
        precondition(c > 32767);
        short s = (short) c;
//      ^^^^^^^^^^^^^^^^^^^^^ Error: cast from char to int16 does not hold
    }

    static void narrowCharToByteOutOfRange(char c) {
        precondition(c > 127);
        byte b = (byte) c;
//      ^^^^^^^^^^^^^^^^^^^ Error: cast from char to int8 does not hold
    }

    // ============================================================
    // Cast nested inside larger expressions. Without the frontend's
    // lift, the intermediate narrowing would be silently accepted.
    // Each also cascades follow-on failures on later checks.
    // ============================================================

    static void narrowInBinaryExpression(int x) {
        int y = ((short) x) + 1;
//               ^^^^^^^^^ Error: cast from int32 to int16 does not hold
        check(y >= -32768 + 1);
    }

    static void narrowInNestedArithmetic(int x) {
        int y = (((short) x) + 1) - 1;
//                ^^^^^^^^^ Error: cast from int32 to int16 does not hold
        check(y == x);
    }

    static void narrowInConditional(int x, int y) {
        int r = x > 0 ? (short) x : (short) y;
//                      ^^^^^^^^^ Error: cast from int32 to int16 does not hold
//                                  ^^^^^^^^^ Error: cast from int32 to int16 does not hold
        check(r == x || r == y);
    }

    // ============================================================
    // Casts in argument and if positions.
    // ============================================================

    @Pure
    static int identity(int v) { return v; }

    static void narrowInMethodArgument(int x) {
        int r = identity((short) x);
//                       ^^^^^^^^^ Error: cast from int32 to int16 does not hold
        check(r == x);
//      ^^^^^^^^^^^^^ Error: assertion does not hold
    }

    static void widenInMethodArgument(short s) {
        int r = identity(s);  // implicit widening; identity has no postcondition
        check(r == s);        // ...so this cascades to a failure
//      ^^^^^^^^^^^^^ Error: assertion does not hold
    }

    static void narrowInIfCondition(int x) {
        int r = 0;
        if (((short) x) > 0) {
//           ^^^^^^^^^ Error: cast from int32 to int16 does not hold
            r = 1;
        }
        check(r == 0 || r == 1);
    }

    // ============================================================
    // Casts in return position.
    // ============================================================

    static int returnsShortNarrowing(int x) {
        return (short) x;
//             ^^^^^^^^^ Error: cast from int32 to int16 does not hold
    }

    // ============================================================
    // Casts in precondition/postcondition expressions.
    //
    // With the function-call design, the cast helper's precondition
    // becomes a proof obligation wherever the call appears -- body,
    // precondition, or postcondition. The postcondition itself then
    // implicates the helper's ensures clause (`$result == x`).
    // ============================================================

    static int assumesFitsInShort(int x) {
        precondition(((short) x) >= 0);
        return x;
    }

    // Postcondition contains a narrowing cast on an unconstrained
    // parameter; the range check inside the postcondition can't be
    // discharged.
    static int postconditionCastFails(int x) {
        postcondition((int res) -> res == (short) x);
//                                 ^^^^^^^^^^^^^^^^ Error: postcondition does not hold
        return x;
    }

    // Same idiom but with a matching precondition. Still fails today
    // because Strata's postcondition-checking doesn't propagate the
    // precondition into the cast helper's call context in this shape.
    // Kept as an aspirational test.
    static int postconditionCastVerifies(int x) {
        precondition(x >= -32768 && x <= 32767);
        postcondition((int res) -> res == (short) x);
//                                 ^^^^^^^^^^^^^^^^ Error: postcondition does not hold
        return (short) x;
    }

    // Postcondition where the cast is on the result binding. `res`
    // (bound to the returned value) isn't constrained to fit short.
    static int postconditionCastOnResultFails(int x) {
        postcondition((int res) -> ((short) res) == res);
//                                 ^^^^^^^^^^^^^^^^^^^^ Error: postcondition does not hold
        return x;
    }

    // ============================================================
    // Chained casts.
    // ============================================================

    static void chainedCastsInRange() {
        long l = 100L;
        byte b = (byte) (int) l;
        check(b == 100);
    }

    static void chainedCastsFirstNarrowingFails(long l) {
        byte b = (byte) (int) l;
//      ^^^^^^^^^^^^^^^^^^^^^^^^ Error: cast from int32 to int8 does not hold
//                      ^^^^^^^ Error: cast from int64 to int32 does not hold
        check(b >= -128 && b <= 127);
    }

    // ============================================================
    // Loop conditions with narrowing casts are intentionally NOT
    // exercised: the synthetic $cast_N local isn't listed as allowed
    // to change from the loop body's modifies-inference perspective,
    // so those programs fail with a different, unrelated error.
    // Tracked as a separate follow-up.
    // ============================================================

    // ============================================================
    // Escape hatch: assume the source is in range, then the cast
    // verifies. Matches how in-type overflow is bypassed today.
    // ============================================================

    static void narrowWithAssume(int i) {
        assume(i >= -32768 && i <= 32767);
        short s = (short) i;
        check(s == i);
    }

    static void narrowInBinaryWithAssume(int x) {
        assume(x >= -32768 && x <= 32767);
        int y = ((short) x) + 1;
        check(y == x + 1);
    }


    /**
     * Regression test for jverify issue #463: JLS §5.6.2 binary numeric
     * promotion for mixed integral operands. Before the fix, a binary
     * operator whose operands had different declared integer widths
     * (e.g. {@code long == int}, {@code long * int}) was lowered to
     * Laurel with the widths preserved. That produced e.g.
     * {@code mul(int64, int32)}, which is not what the JLS says the
     * operator's operand types are; §5.6.2 mandates widening the
     * narrower operand to the wider one before the operator applies.
     * {@link org.strata.jverify.verifier.compiler.simplifications.ImplicitCastInserter}
     * now inserts that widening so the Laurel program matches the JLS
     * and Strata sees a single-width operator on both sides.
     *
     * The assertions here are all tautologies: the fix's purpose is to
     * make the emitted Laurel well-typed per the JLS, not to change
     * verification semantics. The interesting result is that the file
     * verifies with exit code 0.
     */
    static void byteVsIntLiteral() {
        byte b = 127;
        check(b == 127);
    }

    static void longTimesIntLiteral() {
        long big = 3_000_000_000L;
        check(big * 2 == 6_000_000_000L);
    }

    static void longPlusIntLiteral() {
        long big = 3_000_000_000L;
        check(big + 2 == 3_000_000_002L);
    }

    static void longMinusIntLiteral() {
        long big = 3_000_000_000L;
        check(big - 2 == 2_999_999_998L);
    }

    static void longDivIntLiteral() {
        long big = 3_000_000_000L;
        check(big / 2 == 1_500_000_000L);
    }

    static void bytePlusInt() {
        byte b = 127;
        int i = 3;
        check(i+b == 130);
    }

    static void longVsIntEq(long a, int b) {
        check(a == b || a != b);
    }

    static void longVsIntCmp(long a, int b) {
        check(a >= b || a < b);
    }

    static void shortVsIntEq(short s, int i) {
        check(s == i || s != i);
    }

    static void byteVsLongEq(byte b, long l) {
        check(b == l || b != l);
    }

    static void charVsIntEq(char c, int i) {
        check(c == i || c != i);
    }

    static void unboundedTimesInt(@Unbounded long u, int i) {
        check(u * i == i * u);
    }

    static void byteVsShortEq(byte b, short s) {
        check(b == s || b != s);
    }

    static void bytePlusShort(byte b, short s) {
        check(b + s == s + b);
    }

    // JLS §5.6.2 promotes both `byte + byte` operands to int, so the
    // result is int (not byte). Same-width same-tag pairs are left
    // alone by the pass to avoid trivial cast helpers; Strata already
    // types byte+byte as byte here.
    static void bytePlusByteFits() {
        byte b = 100;
        byte c = 100;
        check(b + c == 200);
    }
}
