package org.strata.jverify.verifier.tests.javasupport;

import org.strata.jverify.Pure;
import org.strata.jverify.testengine.JVerifyTest;

/**
 * The mangled procedure name joins the enclosing class and the method with a
 * separator that cannot appear in a Java identifier ('?'), so two distinct
 * methods can never mangle to the same name. These two would collide under a
 * '_' join — {@code Foo_bar.baz} and {@code Foo.bar_baz} both give
 * {@code ...Foo_bar_baz} — but with the '?' separator they are distinct
 * ({@code ...Foo_bar?baz} vs {@code ...Foo?bar_baz}), so both verify.
 *
 * <p>All five methods verify: the two here plus the three implicit constructors
 * (outer, Foo_bar, Foo).
 */
@JVerifyTest(methodsVerified = 5, errorCount = 0)
class MangledNameSeparation {

    static class Foo_bar {
        @Pure
        int baz() {
            return 0;
        }
    }

    static class Foo {
        @Pure
        int bar_baz() {
            return 0;
        }
    }
}
