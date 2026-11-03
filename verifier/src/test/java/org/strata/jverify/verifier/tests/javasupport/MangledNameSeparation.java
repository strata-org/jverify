package org.strata.jverify.verifier.tests.javasupport;

import org.strata.jverify.Transparent;
import org.strata.jverify.testengine.JVerifyTest;

/**
 * {@code Foo_bar.baz} and {@code Foo.bar_baz}, and {@code Foo_bar.qux} and
 * {@code Foo.bar_qux}, join class and name to the same string under a '_' join,
 * for instance and static methods respectively.
 *
 * <p>All seven methods verify: the four here plus the three implicit constructors
 * (outer, Foo_bar, Foo).
 */
@JVerifyTest(methodsVerified = 7, errorCount = 0)
class MangledNameSeparation {

    static class Foo_bar {
        @Transparent
        int baz() {
            return 0;
        }

        static int qux() {
            return 0;
        }
    }

    static class Foo {
        @Transparent
        int bar_baz() {
            return 0;
        }

        static int bar_qux() {
            return 0;
        }
    }
}
