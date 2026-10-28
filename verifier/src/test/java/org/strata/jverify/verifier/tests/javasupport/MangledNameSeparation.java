package org.strata.jverify.verifier.tests.javasupport;

import org.strata.jverify.Transparent;
import org.strata.jverify.testengine.JVerifyTest;

/**
 * The mangled procedure name joins enclosing class and method with '?', which
 * cannot appear in a Java identifier, so distinct methods never mangle alike.
 * These two collide under a '_' join — {@code Foo_bar.baz} and
 * {@code Foo.bar_baz} both give {@code ...Foo_bar_baz} — but with '?' they are
 * distinct ({@code ...Foo_bar?baz} vs {@code ...Foo?bar_baz}), so both verify.
 *
 * <p>All five methods verify: the two here plus the three implicit constructors
 * (outer, Foo_bar, Foo).
 */
@JVerifyTest(methodsVerified = 5, errorCount = 0)
class MangledNameSeparation {

    static class Foo_bar {
        @Transparent
        int baz() {
            return 0;
        }
    }

    static class Foo {
        @Transparent
        int bar_baz() {
            return 0;
        }
    }
}
