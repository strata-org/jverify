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
 * <p>Two lowering paths are covered, because they are distinct in the
 * translator: {@code postcondition(...)} becomes an ensures clause, while
 * {@code check(...)} becomes an assert. Every real {@code jequals} use in the
 * codebase is the postcondition form (see {@code object-contract.java} and the
 * builtin contracts), so that is the path that matters.
 *
 * <p>What holds today: reflexive {@code jequals} proves, and distinct values
 * are correctly not equal. What does not: structural equality. Records lower to
 * opaque composite sorts rather than Core datatypes, so two separately
 * constructed records with equal components compare as distinct references. The
 * expected failures below pin that, and will break (prompting an update) once
 * records lower to datatypes.
 *
 * <p>Not covered: {@code jequals} reached through record component accessors
 * ({@code p.x()}), which emit a call to an undeclared procedure — a separate
 * record-accessor gap.
 *
 * <p>Also not covered: {@code jequals} on primitives. Because
 * {@code <T> jequals(T, T)} cannot bind {@code T} to a primitive, javac
 * autoboxes each argument, and the resulting {@code Integer.valueOf} call fails
 * Laurel resolution — {@code 'java.lang.Integer_valueOf' is not defined}, or
 * {@code 'java.lang.Integer?static_valueOf'} with builtin contracts enabled,
 * where the {@code ?static} name comes from
 * {@link org.strata.jverify.verifier.compiler.simplifications.MoveStaticMethodsToStaticType}
 * re-homing static methods onto a synthetic type whose procedures are not
 * emitted. The {@code jequals} node itself translates fine in both cases; the
 * errors land on the boxed argument positions, two per call. So this is a
 * static-method-on-library-contract gap that would affect any generic method
 * called with a primitive, not something specific to {@code jequals}.
 */
@JVerifyTest(exitCode = 4, errorCount = 2)
class Jequals {

    record Point(int x, int y) {}

    // ---- postcondition / ensures path (the one real contracts use) ----

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

    // ---- check / assert path ----

    // Note: this is a translator-level probe, not idiomatic usage. `check`
    // takes a `boolean`, so its argument is evaluated eagerly, and `jequals`
    // has a throwing body (`VerificationMethodExecutedException`). Real code
    // puts `jequals` inside a contract lambda, which is never invoked.
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
