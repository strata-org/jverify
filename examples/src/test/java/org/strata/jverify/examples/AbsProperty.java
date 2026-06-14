package org.strata.jverify.examples;

import org.strata.jverify.AsProperty;
import org.strata.jverify.Pure;
import net.jqwik.api.ForAll;

import static org.strata.jverify.JVerify.*;

/**
 * Simplest end-to-end @AsProperty example: a pure function with a meaningful
 * property and a cheap precondition. {@code contracts2jqwik} turns the contract
 * below into the jqwik @Property in {@link AbsPropertyGenerated}.
 *
 * <p>The postcondition says the result is non-negative <em>and</em> has the
 * same magnitude as the input — a real property, not a restatement of the
 * one-line body. The precondition excludes {@code Integer.MIN_VALUE}, whose
 * negation overflows: {@code -Integer.MIN_VALUE == Integer.MIN_VALUE}, which is
 * negative and would violate the postcondition. (Drop the precondition and the
 * generated property fails on exactly that input — boundary generation hits it
 * immediately.)</p>
 */
class AbsProperty {

    @AsProperty
    @Pure
    int abs(@ForAll int x) {
        precondition(x != Integer.MIN_VALUE);
        postcondition((Integer r) -> r >= 0 && (r == x || r == -x));
        return x < 0 ? -x : x;
    }
}
