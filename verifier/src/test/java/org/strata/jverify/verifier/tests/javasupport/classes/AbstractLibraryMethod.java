package org.strata.jverify.verifier.tests.javasupport.classes;

import org.strata.jverify.testengine.JVerifyTest;
import org.strata.jverify.testing.AbstractSize;

import static org.strata.jverify.JVerify.check;
import static org.strata.jverify.JVerify.postcondition;

/**
 * {@code AbstractCollection.size()} is abstract, so unlike a concrete library method it does not take precedence over
 * {@code Sized.size()}, whose contract {@code c.size()} takes: every concrete subclass of {@code C} overrides both.
 * The same holds for {@code D}, whose superclass {@code AbstractSize} is on the classpath.
 */
@JVerifyTest(methodsVerified = 6)
class AbstractLibraryMethod {
    interface Sized {
        default int size() {
            postcondition((int r) -> r >= 0);
            return 0;
        }
    }

    abstract static class C extends java.util.AbstractList<Object> implements Sized {
    }

    static void use(C c) {
        check(c.size() >= 0);
    }

    abstract static class D extends AbstractSize implements Sized {
    }

    static void useClasspath(D d) {
        check(d.size() >= 0);
    }
}
