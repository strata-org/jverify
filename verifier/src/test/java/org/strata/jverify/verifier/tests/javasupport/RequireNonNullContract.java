package org.strata.jverify.verifier.tests.javasupport;

import org.strata.jverify.testengine.JVerifyTest;

import java.util.Objects;

import static org.strata.jverify.JVerify.check;

/**
 * {@code Objects.requireNonNull} is bound to the always-injected identity
 * contract, both where the user calls it and where javac inserts it as the
 * enclosing-instance check of a qualified inner allocation.
 */
@JVerifyTest(methodsVerified = 5)
class RequireNonNullContract {
    static class Box {
    }

    class Inner {
    }

    static void userCall() {
        int y = 5;
        Box b = new Box();
        Box c = Objects.requireNonNull(b);
        check(c == b);
        check(y == 5);
    }

    static void qualifiedInnerAllocation(RequireNonNullContract outer) {
        Inner i = outer.new Inner();
    }
}
