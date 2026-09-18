package org.strata.jverify.verifier.tests.javasupport.expressions;

import org.strata.jverify.testengine.JVerifyTest;
import org.strata.jverify.verifier.tests.javasupport.expressions.crossunit.CrossUnitPoint;

import static org.strata.jverify.JVerify.*;

/**
 * Exercises field access across compilation units.
 *
 * <p>{@link CrossUnitPoint} lives in a sibling package; this file only reads
 * and writes its fields. The frontend visits the two compilation units in
 * whatever order it likes, so composite emission has to survive the case
 * where {@code CrossUnitPoint} is referenced (as a parameter or return
 * type) in one unit before a field access on it is seen in a later unit.
 *
 * <p>A regression here would fail resolution with
 * {@code CrossUnitPoint.x not found} because the composite got emitted
 * without its field list.
 */
@JVerifyTest(methodsVerified = 6, errorCount = 1, exitCode = 4, additionalFiles = {"./crossunit/CrossUnitPoint.java"})
public class FieldAccessCrossUnit {

    static void writeFieldOnCrossUnit() {
        CrossUnitPoint p = new CrossUnitPoint();
        p.x = 42;
        check(p.x == 42);
    }

    static void writeFieldOnCrossUnitKO() {
        CrossUnitPoint p = new CrossUnitPoint();
        p.x = 42;
        check(p.x == 41);
//      ^^^^^^^^^^^^^^^^ Error: assertion does not hold
    }

    // A method that references the cross-unit type in its signature but
    // never touches any field on it -- this is the exact pattern that
    // triggered the AutoSDE-reported ordering bug: the composite was
    // emitted without fields when the parameter type is first seen,
    // then a later field access failed to resolve.
    static void takesCrossUnitByRef(CrossUnitPoint p) {
        // no field access
    }

    static void writeThenReadDifferentFields() {
        CrossUnitPoint p = new CrossUnitPoint();
        p.x = 3;
        p.y = 4;
        check(p.x + p.y == 7);
    }

    static void chainedFieldAccessThroughCrossUnitType(CrossUnitPoint p) {
        p.x = 10;
        int r = p.x;
        check(r == 10);
    }
}
