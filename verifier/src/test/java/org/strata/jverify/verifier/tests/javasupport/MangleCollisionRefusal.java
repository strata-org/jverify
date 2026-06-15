package org.strata.jverify.verifier.tests.javasupport;

import org.strata.jverify.Pure;
import org.strata.jverify.testengine.JVerifyTest;

/**
 * Two distinct INSTANCE methods whose flat {@code Class_method} mangled names
 * collide ({@code Foo_bar.baz} and {@code Foo.bar_baz} both → {@code ...Foo_bar_baz})
 * must not both be emitted — a duplicate Laurel symbol would make Strata abort
 * abnormally. Both colliding methods are refused (skipped) instead, for a
 * graceful outcome. (Static methods are namespaced by a {@code ?static}
 * separator and so cannot collide this way; only instance methods keep the flat
 * scheme.) Disambiguating mangled names is future work.
 */
@JVerifyTest(continueOnErrors = true, exitCode = 0, methodsVerified = 3, methodsSkipped = 2, errorCount = 0)
class MangleCollisionRefusal {

    // Both colliding methods are instance methods, so the collision refusal is a
    // SILENT skip (no diagnostic) — only the count reflects it: the three implicit
    // constructors (outer + Foo_bar + Foo) stay verified, the two colliding
    // methods (baz, bar_baz) skip.
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
