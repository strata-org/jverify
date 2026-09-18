package org.strata.jverify.verifier.tests.javasupport;

import org.strata.jverify.testengine.JVerifyTest;
import org.strata.jverify.*;

import static org.strata.jverify.JVerify.*;

/**
 * Translator support for {@code JVerify.jequals}, observational equality:
 * {@code ==} on primitives, reference equality on impure composites, recursive
 * structural equality on pure ones. Laurel's {@code Eq} operation is specified
 * with exactly those semantics, so {@code jequals} lowers to the same node the
 * translator emits for Java {@code ==}.
 *
 * <p>Three usage positions are covered, since they take different routes through
 * the translator. In rough order of how much they matter:
 * <ol>
 *   <li>a {@code @Pure} method's return expression, which becomes a Laurel
 *       function body — the form {@code RecordContract.equals} and
 *       {@code StringContract.equals} use in {@code object-contract.java};</li>
 *   <li>{@code postcondition(...)}, which becomes an ensures clause — the form
 *       the builtin collection contracts use;</li>
 *   <li>{@code check(...)}, which becomes an assert.</li>
 * </ol>
 *
 * <p>What holds today: reflexive {@code jequals} proves, and distinct values are
 * correctly not equal. What does not: structural equality. Records lower to
 * opaque composite sorts rather than Core datatypes, so two separately
 * constructed records with equal components compare as distinct references. The
 * expected failures below pin that, and will break (prompting an update) once
 * records lower to datatypes.
 *
 * <p>Two things deliberately uncovered, both gaps in other subsystems rather
 * than in {@code jequals}. Reaching a record's components through its accessors
 * ({@code p.x()}) inside a contract emits a call to a procedure that was never
 * declared. And {@code jequals} on primitives is unreachable: {@code <T>
 * jequals(T, T)} cannot bind {@code T} to a primitive, so javac autoboxes, and
 * the synthetic autobox helper fails Laurel resolution because
 * {@link org.strata.jverify.verifier.compiler.simplifications.MoveStaticMethodsToStaticType}
 * re-homes a library contract's static methods onto a synthetic type whose
 * procedures are not emitted. In both cases the {@code jequals} node itself
 * translates fine; the errors land elsewhere in the expression.
 */
@JVerifyTest(exitCode = 4, methodsVerified = 11, methodsInvalid = 2, errorCount = 2)
class Jequals {

    record Point(int x, int y) {}

    // ---- 1. @Pure return expression (what object-contract.java's equals uses) ----

    @Pure
    static boolean sameAs(Point a, Point b) {
        return jequals(a, b);
    }

    /** Exercises `sameAs`'s body at a call site: reflexivity must carry through. */
    static void returnExpressionFormIsUsable(Point p) {
        check(sameAs(p, p));
    }

    // ---- 2. postcondition / ensures path ----

    /** The postcondition is the obligation: `jequals(p, p)` must prove from `return p`. */
    @Pure
    static Point identity(Point p) {
        postcondition((Point r) -> jequals(r, p));
        return p;
    }

    /**
     * A freshly constructed record with the same components cannot be shown
     * `jequals` to the input, because equality is still reference equality.
     */
    @Pure
    static Point rebuild(Point p) {
        postcondition((Point r) -> jequals(r, p));
//                                 ^^^^^^^^^^^^^ Error: postcondition does not hold
        return new Point(p.x(), p.y());
    }

    // ---- 3. check / assert path ----

    // Note: this position is a translator-level probe, not idiomatic usage.
    // `check` takes a `boolean`, so its argument is evaluated eagerly, and
    // `jequals` has a throwing body (`VerificationMethodExecutedException`).
    // Real code puts `jequals` in a contract lambda or a @Pure body, neither of
    // which is invoked at runtime.
    static void reflexiveUnderCheck(Point p) {
        check(jequals(p, p));
    }

    static void distinctRecordsAreNotJequal() {
        var p = new Point(1, 2);
        var q = new Point(1, 3);
        check(!jequals(p, q));
    }

    static void structurallyEqualRecordsAreNotYetJequal() {
        var p = new Point(1, 2);
        var q = new Point(1, 2);
        check(jequals(p, q));
//      ^^^^^^^^^^^^^^^^^^^^ Error: assertion does not hold
    }

    // Negation direction, and the reason the pure case is *unsound* rather than
    // merely incomplete: records lower to composites and `Eq` on a composite is
    // reference equality, so the verifier proves `!jequals(p, q)` for two
    // structurally-equal records, treats the guarded branch as dead, and never
    // checks the assertion. The program verifies clean even though the branch is
    // live and the assertion is false at runtime under the documented `jequals`
    // contract (JVerify.java). This method therefore VERIFIES (it contributes to
    // methodsVerified, not to errorCount) -- that success is the bug. It will
    // stop verifying, and must be revisited, once records lower to structural
    // equality in Laurel.
    static void negationOverPureOperandsIsUnsound() {
        var p = new Point(5, 6);
        var q = new Point(5, 6);
        if (jequals(p, q)) {
            check(1 == 2);
        }
    }

    // ---- impure composite, for contrast ----

    // A plain class is an impure composite -- purity follows the declaration
    // kind, not the field contents, so a field-less class is still impure (see
    // `examples/.../TypePurity.java`: `record RecordsArePure`, `class
    // ClassesAreImpure`). Reference equality is therefore the *correct*
    // semantics here, unlike the record cases above.
    //
    // These pass for the same reason the record cases fail, which is the point:
    // the two are indistinguishable today. `Laurel.composite(name, extending,
    // fields, procedures)` has no purity parameter and the translator emits
    // that one form for every class and record, so nothing downstream can tell
    // a pure composite from an impure one.
    static class Box {}

    static void reflexiveOnClass(Box b) {
        check(jequals(b, b));
    }

    static void distinctClassInstancesAreNotJequal() {
        var a = new Box();
        var b = new Box();
        check(!jequals(a, b));
    }
}
