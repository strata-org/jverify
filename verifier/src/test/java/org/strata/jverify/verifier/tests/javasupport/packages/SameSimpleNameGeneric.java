package org.strata.jverify.verifier.tests.javasupport.packages;

import org.strata.jverify.testengine.JVerifyTest;
import org.strata.jverify.verifier.tests.javasupport.packages.samename.a.Foo;

/** Two classes with one simple name stay apart in generic methods' signatures. */
@JVerifyTest(methodsVerified = 9, errorCount = 0, additionalFiles = {"./samename/a/Foo.java", "./samename/b/Foo.java"})
public class SameSimpleNameGeneric {
    static <T> Foo first(Foo f, T t) {
        return f;
    }

    static <T> org.strata.jverify.verifier.tests.javasupport.packages.samename.b.Foo second(
            org.strata.jverify.verifier.tests.javasupport.packages.samename.b.Foo f, T t) {
        return f;
    }

    static void passes(Foo a, org.strata.jverify.verifier.tests.javasupport.packages.samename.b.Foo b) {
        first(a, a);
        second(b, b);
    }
}
