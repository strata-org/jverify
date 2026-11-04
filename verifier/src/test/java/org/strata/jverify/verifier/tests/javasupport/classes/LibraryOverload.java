package org.strata.jverify.verifier.tests.javasupport.classes;

import org.strata.jverify.testengine.JVerifyTest;
import org.strata.jverify.testing.InheritsFoo;

import static org.strata.jverify.JVerify.check;
import static org.strata.jverify.JVerify.postcondition;

/**
 * javac binds the first two calls to {@code Random.nextInt(int)}, which no program class declares, so both are refused:
 * {@code C.nextInt(long)}, whose contract would prove the check, is another overload, and {@code D} does not inherit
 * {@code Dice.nextInt(int)}, as a class's method takes precedence over an interface's. The same holds for a class on
 * the classpath: {@code E} runs {@code ContainsFoo.foo()}, which returns 3. A library method with other parameters
 * takes no precedence: {@code d.nextInt(10L)} and {@code e.foo(5)} run the interfaces' defaults.
 */
@JVerifyTest(continueOnErrors = true, methodsVerified = 11, methodsSkipped = 3)
class LibraryOverload {
    interface Dice {
        default int nextInt(int bound) {
            postcondition((int r) -> r == -1);
            return -1;
        }

        default int nextInt(long bound) {
            postcondition((int r) -> r == -2);
            return -2;
        }
    }

    static class C extends java.util.Random {
        public int nextInt(long bound) {
            postcondition((int r) -> r == -1);
            return -1;
        }
    }

    static class D extends java.util.Random implements Dice {
    }

    static void overload(C c) {
//                            ^ error: call to library method 'nextInt' is not yet supported
        check(c.nextInt(10) == -1);
    }

    static void inherited(D d) {
//                             ^ error: call to library method 'nextInt' is not yet supported
        check(d.nextInt(10) == -1);
    }

    static void otherParameters(D d) {
        check(d.nextInt(10L) == -2);
    }

    interface Fooer {
        default int foo() {
            postcondition((int r) -> r == 1);
            return 1;
        }

        default int foo(int k) {
            postcondition((int r) -> r == 2);
            return 2;
        }
    }

    static class E extends InheritsFoo implements Fooer {
    }

    static void inheritedFromClasspath(E e) {
//                                          ^ error: call to library method 'foo' is not yet supported
        check(e.foo() == 1);
    }

    static void otherParametersOnClasspath(E e) {
        check(e.foo(5) == 2);
    }
}
