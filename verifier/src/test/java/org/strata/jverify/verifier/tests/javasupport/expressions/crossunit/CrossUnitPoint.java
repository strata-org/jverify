package org.strata.jverify.verifier.tests.javasupport.expressions.crossunit;

/**
 * Companion class for {@link org.strata.jverify.verifier.tests.javasupport.expressions.FieldAccessCrossUnit}.
 * Declared in a separate compilation unit so the composite-emission pass has
 * to keep track of field references it hasn't seen yet when it visits this
 * unit -- see the test class's Javadoc for the ordering invariant this
 * exercises.
 */
public class CrossUnitPoint {
    public int x;
    public int y;
}
