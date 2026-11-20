package org.strata.jverify.verifier.tests.javasupport.statics;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * A call to a static method initializes the class that declares the method,
 * not the class the call names (JLS 12.4.1): {@code Sub.foo()} initializes
 * {@code Super} only, so the first use of {@code Sub} is the read of
 * {@code Sub.s}, which runs its initializer and changes {@code log}.
 */
@JVerifyTest(continueOnErrors = true, exitCode = 4, methodsInvalid = 1, errorCount = 1)
class InheritedStaticMethodInitializesDeclaringClass {

    static int log;

    static class Super {
        static int foo() {
            return 0;
        }
    }

    static class Sub extends Super {
        static int s;

        static {
            log = 1;
        }
    }

    static void m() {
        Sub.foo();
        log = 2;
        int x = Sub.s;
        check(log == 2);
//      ^^^^^^^^^^^^^^^^ Error: assertion does not hold; a static field may have been changed by the static initializer of 'Sub', which this method may run
    }
}
