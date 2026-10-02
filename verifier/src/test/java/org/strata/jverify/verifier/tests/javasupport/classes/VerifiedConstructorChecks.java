package org.strata.jverify.verifier.tests.javasupport.classes;

import org.strata.jverify.testengine.JVerifyTest;

/**
 * A verified constructor is refused for the same reasons, and with the same
 * diagnostics, as a verified method with the identical body.
 */
@JVerifyTest(continueOnErrors = true, exitCode = 0, methodsVerified = 1, methodsSkipped = 4, errorCount = 0)
class VerifiedConstructorChecks {
    static class UndeclaredThrow {
        UndeclaredThrow(int x) {
//      ^ warning: instance method '<init>' was not verified: throw requires the enclosing method to declare the thrown type in its throws clause
            if (x > 0) {
                throw new IllegalArgumentException();
            }
        }

        void m(int x) {
//           ^ warning: instance method 'm' was not verified: throw requires the enclosing method to declare the thrown type in its throws clause
            if (x > 0) {
                throw new IllegalArgumentException();
            }
        }
    }

    static class FloatLocal {
        int v;

        FloatLocal(int x) {
//      ^ warning: instance method '<init>' was not verified: Unsupported type: float
            float f = x;
            this.v = (int) f;
        }

        void m(int x) {
//           ^ warning: instance method 'm' was not verified: Unsupported type: float
            float f = x;
            this.v = (int) f;
        }
    }
}
