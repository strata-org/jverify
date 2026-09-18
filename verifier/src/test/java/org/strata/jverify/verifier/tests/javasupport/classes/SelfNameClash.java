package org.strata.jverify.verifier.tests.javasupport.classes;

import org.strata.jverify.Unbounded;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * User parameters and locals named {@code self} or {@code $self} cannot collide
 * with the synthesized instance-method receiver: the binder is {@code self?},
 * whose {@code ?} is outside Java's identifier grammar (JLS 3.8). Regression:
 * with a plain {@code self} binder, Strata rejected the duplicate parameter
 * definition at an unattributable location while the run still reported the
 * method Verified; a {@code $self} binder had the same flaw ({@code $} IS legal
 * in Java identifiers). Verified 5 = four methods + implicit constructor.
 */
@JVerifyTest(methodsVerified = 5, methodsSkipped = 0, errorCount = 0)
final class SelfNameClash {

    @Unbounded
    int viaParam(@Unbounded int self) {
        check(self + 1 > self);
        return self;
    }

    @Unbounded
    int viaLocal(@Unbounded int a) {
        @Unbounded int self = a;
        check(self == a);
        return a;
    }

    @Unbounded
    int viaDollarParam(@Unbounded int $self) {
        check($self + 1 > $self);
        return $self;
    }

    @Unbounded
    int viaDollarLocal(@Unbounded int a) {
        @Unbounded int $self = a;
        check($self == a);
        return a;
    }
}
