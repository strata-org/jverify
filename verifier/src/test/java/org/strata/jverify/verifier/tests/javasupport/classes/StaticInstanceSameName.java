package org.strata.jverify.verifier.tests.javasupport.classes;

import org.strata.jverify.Unbounded;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * A static and an instance method may share a name without colliding: the static
 * moves to the {@code Class?static} sibling and mangles to
 * {@code Class?static?foo}, the instance method to {@code Class?foo}. Regression
 * for a symbol-table bug where the moved static's stale scope entry made
 * refuseIfOverloaded miscount and silently drop the instance method (counted
 * Skipped, no diagnostic). Both must verify: 3 = static foo + instance foo +
 * implicit constructor. The class is {@code final} so the instance method is
 * translated (not refused for polymorphic dispatch).
 */
@JVerifyTest(methodsVerified = 3, methodsSkipped = 0, errorCount = 0)
final class StaticInstanceSameName {

    @Unbounded
    static int foo(@Unbounded int x) {
        check(x == x);
        return x;
    }

    @Unbounded
    int foo() {
        check(1 == 1);
        return 1;
    }
}
