package org.strata.jverify.verifier.tests.javasupport.lambdas;

import org.strata.jverify.Contract;
import org.strata.jverify.ContractException;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * A lambda that captures {@code this} in a generic class is refused, at the
 * lambda, rather than emitted as an override whose captured receiver names the
 * generic class without its type parameters (which failed Strata resolution and
 * stopped verification of the whole program). The rest of the program still
 * verifies.
 */
@JVerifyTest(methodsVerified = 3, errorCount = 0)
public class LambdaCapturesThisInGenericClass<T> {

    interface Grow {
        int apply(int x);

        @Contract
        class GrowContract implements Grow {
            @Override
            public int apply(int x) {
                precondition(x >= 0 && x < 1000);
                postcondition((int r) -> r > x);
                throw new ContractException();
            }
        }
    }

    int field = 1;

    static void useGrow(Grow g) {
        g.apply(5);
    }

    void captureThis() {
        useGrow(x -> x + 1 + (field - field));
//              ^ warning: instance method 'apply' was not verified: a lambda that captures 'this' in a generic class is not yet supported
    }

    // A lambda that does not capture `this` is still checked.
    static void noCapture() {
        useGrow(x -> x + 1);
    }
}
