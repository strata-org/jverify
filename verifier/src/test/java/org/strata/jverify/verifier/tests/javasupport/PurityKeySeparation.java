package org.strata.jverify.verifier.tests.javasupport;

import org.strata.jverify.Transparent;
import org.strata.jverify.Verify;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.postcondition;
import static org.strata.jverify.JVerify.precondition;

/**
 * {@code Foo.bar_baz} writes the heap and {@code Foo_bar.baz} is {@code @Transparent}. A '_' join gives both
 * the purity key {@code ...Foo_bar_baz()I}, which would read {@code top}'s call as heap-free and let
 * {@code outer}'s false postcondition verify. {@code top}, which writes the heap, cannot be translated, so
 * {@code outer} is dropped with it.
 */
@JVerifyTest(continueOnErrors = true, exitCode = 0, methodsVerified = 5, methodsSkipped = 2, errorCount = 0)
class PurityKeySeparation {

    static class Foo {
        int f;

        int bar_baz() {
            this.f = 1;
            return 0;
        }
    }

    static class Foo_bar {
        @Transparent
        int baz() {
            return 0;
        }
    }

    @Transparent
    @Verify(false)
    static int top(Foo o) {
        return o.bar_baz();
    }

    static int outer(Foo o) {
//             ^ error: call to 'top', which could not be translated
        precondition(o.f == 0);
        postcondition((int r) -> o.f == 0);
        return top(o);
    }
}
