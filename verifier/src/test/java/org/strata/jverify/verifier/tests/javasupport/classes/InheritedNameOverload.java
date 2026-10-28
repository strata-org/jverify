package org.strata.jverify.verifier.tests.javasupport.classes;

import org.strata.jverify.Unbounded;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * Overloading the name of an INHERITED method is disambiguated like any other
 * overload, even when one declaration comes from a library type the user never
 * wrote.
 *
 * <p>{@code Object} declares {@code toString()}; {@code Holder} adds
 * {@code toString(int)}. Two erased signatures across {@code Holder}'s closure,
 * so {@code Holder.toString(int)} is emitted as {@code toString$int} while the
 * inherited {@code Object.toString()} keeps the bare name. {@code h.toString(1)}
 * binds {@code Holder.toString(int)}, emits {@code toString$int}, and verifies.
 *
 * <p>Verified 4 = {@code toString(int)} + {@code root} + implicit constructors
 * for {@code InheritedNameOverload} and {@code Holder}.
 */
@JVerifyTest(continueOnErrors = true, exitCode = 0, methodsVerified = 4, errorCount = 0)
public class InheritedNameOverload {

    static final class Holder {
        @Unbounded int toString(int x) { postcondition((int r) -> r == 5); return 5; }
    }

    static void root(Holder h) {
        check(h.toString(1) == 5);
    }
}
