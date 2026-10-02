package org.strata.jverify.verifier.tests.javasupport.generics;

import org.strata.jverify.Pure;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(exitCode = 4, methodsVerified = 0, methodsInvalid = 1, errorCount = 1, methodsSkipped = 5)
class OverrideOfGenericInstantiation {
    static class BaseQ<T> {
        @Pure int m(T x) { postcondition((int r) -> r >= 0); return 1; }
    }
    static class SubQ extends BaseQ<Integer> {
        @Pure @Override int m(Integer x) { return -1; }
//      ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ Error: dynamic dispatch does not yet support an override of a generic method whose type parameters differ from the base: 'org.strata.jverify.verifier.tests.javasupport.generics.OverrideOfGenericInstantiation.SubQ' declares type parameters [] but the overridden generic 'org.strata.jverify.verifier.tests.javasupport.generics.OverrideOfGenericInstantiation.BaseQ' declares [T]. An overriding composite must repeat the base's type parameters verbatim (e.g. `SBox<T> extends Box<T>`).
    }
    static void root(Integer i) {
        BaseQ<Integer> b = new SubQ();
        check(b.m(i) >= 0);
    }
}
