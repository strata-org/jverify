package org.strata.jverify.verifier.tests.javasupport.lambdas;

import org.strata.jverify.Contract;
import org.strata.jverify.ContractException;
import org.strata.jverify.Transparent;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * Soundness regression: a lambda's body is checked against its functional
 * interface's contract. A call through the interface ({@code useGrow}) reasons
 * only from that contract, so a lambda that breaks it would otherwise make a
 * false claim verify. Each lambda below is the override of {@code Grow.apply}:
 * it assumes the interface's precondition and must establish its postcondition,
 * and every assertion in its body is checked.
 *
 * <p>A captured local is unconstrained inside the body, which is checked for
 * every value the local could hold.
 */
@JVerifyTest(exitCode = 4, methodsVerified = 7, errorCount = 7)
public class LambdaBodyContract {

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

    private int field = 7;

    static void useGrow(Grow g) {
        g.apply(5);
    }

    // Transparent, so a lambda may call it under Grow.apply's empty frame.
    @Transparent
    static void take(Grow g) {
    }

    static void breaksPostcondition() {
        useGrow(x -> x - 1);
//                   ^^^^^ Error: postcondition could not be proved
    }

    static void failingCheckInBody() {
        useGrow(x -> {
            check(x > 100);
//          ^^^^^^^^^^^^^^^ Error: assertion could not be proved
            return x + 1;
        });
    }

    static void overflowInBody() {
        useGrow(x -> x * 1000000000 + 1000000000);
//                   ^^^^^^^^^^^^^^^^^^^^^^^^^^^ Error: assertion could not be proved
//                   ^^^^^^^^^^^^^^^^^^^^^^^^^^^ Error: no int overflow on '*' could not be proved
//                   ^^^^^^^^^^^^^^^^^^^^^^^^^^^ Error: no int overflow on '+' could not be proved
//                   ^^^^^^^^^^^^^^^^^^^^^^^^^^^ Error: postcondition could not be proved
    }

    static void keepsContract() {
        useGrow(x -> x + 1);
    }

    static void keepsContractInBlock() {
        useGrow(y -> {
            int z = y * 2;
            return z + 1;
        });
    }

    static void captureCorrectForAnyValue() {
        int k = 3;
        useGrow(x -> k > 0 ? x + 1 : x + 2);
    }

    void captureThis() {
        useGrow(x -> x + 1 + (field - field));
    }

    static void nestedBreaksPostcondition() {
        useGrow(x -> {
            take(y -> y);
//                    ^ Error: postcondition could not be proved
            return x + 1;
        });
    }
}
