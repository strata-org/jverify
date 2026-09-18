package org.strata.jverify.verifier.tests.javasupport.expressions;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * Exercises {@code modifies(...)} clauses on procedures that write instance
 * fields. Complements {@link FieldAccess}, which covers reads and writes in
 * a single method body; here we focus on call-site framing.
 *
 * <p>For each shape, one method is a callee with a specific {@code modifies}
 * clause and a matching {@code postcondition} pinning the new value; a
 * companion caller verifies that (a) the postcondition transfers, (b) fields
 * not named in the clause survive, and (c) fields that WERE named are
 * havoc'd (checks that would need those fields fixed fail).
 */
@JVerifyTest(methodsVerified = 15, errorCount = 4, exitCode = 4)
class FieldAccessModifies {
    static class Point {
        int x;
        int y;
    }

    // ---- Case 1: modifies the whole object -------------------------------

    static void writesXModifiesObj(Point p) {
        modifies(p);
        postcondition(() -> p.x == 42);
        p.x = 42;
    }

    /** Caller sees the postcondition, but p.y is havoc'd by modifies(p). */
    static void callerModifiesObj() {
        Point p = new Point();
        p.x = 1;
        p.y = 5;
        writesXModifiesObj(p);
        check(p.x == 42);
    }

    /** With modifies(p), y is also havoc'd, so a check on it fails. */
    static void callerModifiesObjHavocsYKO() {
        Point p = new Point();
        p.y = 5;
        writesXModifiesObj(p);
        check(p.y == 5);
//      ^^^^^^^^^^^^^^^ Error: assertion does not hold
    }

    // ---- Case 2: no modifies clause on a body that writes fields --------
    //
    // The frontend synthesizes `modifies *` for us. That is sound but
    // opaque -- callers cannot rely on ANY field surviving.

    /** No user-supplied modifies; the frontend adds a wildcard. */
    static void writesXNoModifies(Point p) {
        postcondition(() -> p.x == 42);
        p.x = 42;
    }

    /** Postcondition still transfers even under the synthesized wildcard. */
    static void callerNoModifiesPostOK() {
        Point p = new Point();
        writesXNoModifies(p);
        check(p.x == 42);
    }

    /** Nothing else can be assumed: p.y is havoc'd. */
    static void callerNoModifiesFieldHavocedKO() {
        Point p = new Point();
        p.y = 7;
        writesXNoModifies(p);
        check(p.y == 7);
//      ^^^^^^^^^^^^^^^ Error: assertion does not hold
    }

    // ---- Case 3: two objects, modifies names only one -------------------

    static void writesToFirst(Point p, Point q) {
        modifies(p);
        postcondition(() -> p.x == 100);
        p.x = 100;
    }

    /** q is untouched: its fields survive across the call. */
    static void callerOtherObjectSurvives() {
        Point p = new Point();
        Point q = new Point();
        q.x = 9;
        q.y = 10;
        writesToFirst(p, q);
        check(p.x == 100);
        check(q.x == 9);
        check(q.y == 10);
    }

    // ---- Case 4: caller passes an aliased pair -------------------------
    //
    // When p and q are actually the same reference, a modifies(p) clause
    // is also a modifies of everything reachable via q. A field read on
    // q after the call cannot be assumed to hold its pre-call value.

    static void callerAliasedHavocKO() {
        Point p = new Point();
        Point q = p;
        q.y = 11;
        writesToFirst(p, q);
        // q aliases p, and modifies(p) may have changed anything at that
        // address -- including q.y.
        check(q.y == 11);
//      ^^^^^^^^^^^^^^^^ Error: assertion does not hold
    }

    // ---- Case 5: modifies matches the write; body verifies ------------

    static void modifiesMatchesBody(Point p) {
        modifies(p);
        p.x = 7;
    }

    /** No caller: purely a body-side test. The frame check accepts p.x
     *  because p is in the modifies clause. */
    static void modifiesMatchesBodyCaller() {
        Point p = new Point();
        modifiesMatchesBody(p);
        // We can't assert p.x's value (no postcondition), but the call
        // itself must be well-formed.
    }

    // ---- Case 6: impure call INSIDE a postcondition lambda --------------
    //
    // bodyPotentiallyWritesHeap must not descend into
    // precondition/postcondition/invariant/reads/modifies/decreases
    // subtrees. Otherwise an impure call in a *spec* position (not a
    // runtime effect) would trigger the synthesized `modifies *`
    // fallback and demote otherwise-clean callees to OpaqueSpec.

    /** A non-Pure helper; treating it as heap-writing would poison any
     *  method that mentions it, even inside a postcondition lambda. */
    static boolean impureBooleanHelper() { return true; }

    /** Callee's body is empty, but its postcondition lambda calls an
     *  impure method. Before the fix, this method's callers lost frame
     *  precision because `bodyPotentiallyWritesHeap` scanned the whole
     *  method (spec included) and synthesized `modifies *`. */
    static void cleanBodyImpurePostcondLambda(Point p) {
        postcondition(() -> impureBooleanHelper() == impureBooleanHelper());
    }

    /** Caller can now prove p.x survives the call because the callee
     *  has no synthesized `modifies *`. */
    static void callerOfCleanBodyImpurePostcondLambda() {
        Point p = new Point();
        p.x = 1;
        cleanBodyImpurePostcondLambda(p);
        check(p.x == 1);
    }

    // ---- Case 7: modifies(everything()) ---------------------------------
    //
    // `everything()` is a JVerify spec-only stub. Sending it through
    // convertExpression would throw "Unsupported JVerify method: everything";
    // instead the frontend recognises `modifies(everything())` up front and
    // maps it to Laurel's modifiesWildcard. Same semantics as omitting the
    // modifies clause on a body that writes fields (both synthesize
    // modifiesWildcard), so the two spellings are interchangeable at the
    // call site.

    /** Explicit `modifies(everything())` on a callee that writes a field. */
    static void writesEverything(Point p) {
        modifies(everything());
        postcondition(() -> p.x == 42);
        p.x = 42;
    }

    /** Postcondition transfers under the wildcard frame. */
    static void callerOfWritesEverythingOK() {
        Point p = new Point();
        writesEverything(p);
        check(p.x == 42);
    }

    /** Field NOT named by the postcondition is havoc'd under the wildcard,
     *  matching Case 2 (no-modifies) behavior. */
    static void callerOfWritesEverythingHavocsYKO() {
        Point p = new Point();
        p.y = 5;
        writesEverything(p);
        check(p.y == 5);
//      ^^^^^^^^^^^^^^^ Error: assertion does not hold
    }
}
