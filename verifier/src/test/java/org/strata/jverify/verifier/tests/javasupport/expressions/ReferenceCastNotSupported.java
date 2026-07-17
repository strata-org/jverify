package org.strata.jverify.verifier.tests.javasupport.expressions;

import org.strata.jverify.testengine.JVerifyTest;

/**
 * Reference casts (like `(Circle) shape`) are outside the CR's stated scope
 * -- we only handle primitive integer casts today. `convertTypeCast` therefore
 * must fall through to the unsupported-expression path, producing a single
 * clean diagnostic at the cast site.
 *
 * <p>Regression guard: an earlier iteration accepted any target that
 * translated to a Laurel `CompositeType` (which includes reference types),
 * then emitted an `ensures $result == x` where `$result` and `x` had
 * different composite sorts. Strata rejected that during resolution and
 * surfaced a duplicated "cannot compare A with B using '=='" diagnostic --
 * once at the helper's synthetic (1:1-1:1) range and once at the cast site
 * -- instead of the clean pre-CR "Unsupported expression" error.
 */
@JVerifyTest(exitCode = 2, errorCount = 1)
class ReferenceCastNotSupported {

    interface Shape {}
    static class Circle implements Shape {}

    static void refCast(Shape s) {
//              ^ error: Cast to org.strata.jverify.verifier.tests.javasupport.expressions.ReferenceCastNotSupported.Circle is not yet supported
        Circle c = (Circle) s;
    }
}
