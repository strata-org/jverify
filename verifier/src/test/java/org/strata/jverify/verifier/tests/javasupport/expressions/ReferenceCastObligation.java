package org.strata.jverify.verifier.tests.javasupport.expressions;

import org.strata.jverify.testengine.JVerifyTest;

/**
 * Reference casts on composites this compilation declares.
 *
 * <p>These used to be refused outright ("Cast to ... is not yet supported"),
 * because only primitive integer casts were translated. A downcast is now
 * Laurel's {@code as}, whose embedded type test becomes a real obligation, and an
 * upcast is the identity -- every composite declares its full supertype closure,
 * so a subtype value already flows where the supertype is expected.
 *
 * <p>Regression guard, inherited from the fixture this replaces: a reference cast
 * must never go through the {@code java?cast_to_*} HELPER path. An earlier
 * iteration accepted any target translating to a composite and emitted
 * {@code ensures $result == x} across two different composite sorts, which Strata
 * rejected at resolution and reported twice -- once at the helper's synthetic
 * (1:1) range and once at the cast site. Neither arm below builds a helper.
 */
@JVerifyTest(exitCode = 4, errorCount = 1)
class ReferenceCastObligation {

    interface Shape {}
    static class Circle implements Shape {}

    /** Unguarded downcast: `s` is an arbitrary Shape, so the type test cannot be discharged. */
    static void refDowncastUnproven(Shape s) {
        Circle c = (Circle) s;
//      ^^^^^^^^^^^^^^^^^^^^^^ Error: precondition could not be proved
    }

    /** An upcast carries no obligation at all. */
    static Shape refUpcast(Circle c) {
        return (Shape) c;
    }
}
