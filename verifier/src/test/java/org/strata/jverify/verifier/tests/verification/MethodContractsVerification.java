package org.strata.jverify.verifier.tests.verification;

import org.strata.jverify.Contract;
import org.strata.jverify.ContractException;
import org.strata.jverify.Pure;
import org.strata.jverify.testengine.JVerifyTest;

import java.util.function.IntPredicate;

import static org.strata.jverify.JVerify.postcondition;
import static org.strata.jverify.JVerify.precondition;

@JVerifyTest(methodsVerified = 8, errorCount = 0)
public class MethodContractsVerification {

    private int y;

    public int methodReferencePostCondition() {
        postcondition((IntPredicate)MethodContractsVerification::isEven);

        // Include lambda based post-condition because it introduces a return value name
        postcondition((int r) -> r == 2);

        return 2;
    }

    public int differentReturnValueNames() {
        postcondition((int r) -> r > 1);
        postcondition((int v) -> v < 3);

        return 2;
    }

    @Pure
    public static boolean isEven(int x) {
        return x % 2 == 0;
    }

    @Pure
    public static boolean truth() {
        return true;
    }

    /**
     * A {@code @Pure} method that also carries a postcondition. Strata rejects a
     * {@code function} that declares postconditions, so such a method must be
     * emitted as an opaque {@code procedure} (keyed off whether it can stay
     * transparent), not a {@code function}. Before that fix this failed
     * translation; this method is the regression guard.
     */
    @Pure
    public static int pureWithPostcondition(int x) {
        postcondition((int r) -> r == x);
        return x;
    }

    int postconditionNameClash(int x) {
        postcondition((int res) -> res == x);
        int res;
        res = x;
        return res;
    }

    int preconditionLambdas(int x) {
        precondition(() -> {
            return x > 0;
        });
        precondition(MethodContractsVerification::truth);
        int res;
        res = x;
        return res;
    }

    // 0-argument postcondition (postcondition(BooleanSupplier)): the lambda
    // takes no parameters and captures the enclosing scope directly rather
    // than binding the return value. Paired with a 0-argument precondition.
    int zeroArgPostcondition(int x) {
        precondition(() -> x > 0);
        postcondition(() -> x > 0);
        return x;
    }

    /**
     * There was previously a bug where the postcondition created for this method would return 
     * a boxed boolean instead of a regular boolean.
     * This was because the body of the lambda still thought it had to return a Object, instead of a boolean.
     */
    public Object outerMethodReturnsAnObject() {
        // This was failing when we did not enable Lower to handle Lambdas
        postcondition((Object r) -> {
            // The block body is important to trigger Lower.visitReturn
            return true;
        });
        // Returning a non-primitive type is important to trigger boxing
        return new Object();
    }

    public boolean lambdaInPostcondition(int x) {
        postcondition((boolean r) -> r == useIntPredicate(i -> i == 2, x));
        return x == 2;
    }

    @Pure
    boolean useIntPredicate(IntPredicate predicate, int i) {
        return predicate.test(i);
    }

    @Contract
    static abstract class IntPredicateContract implements IntPredicate {
        @Pure
        @Override
        public boolean test(int value) {
            throw new ContractException();
        }
    }
}
