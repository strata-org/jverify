package com.aws.jverify.verifier.tests.verification;

import com.aws.jverify.JVerify;
import com.aws.jverify.Pure;
import com.aws.jverify.testengine.JVerifyTest;

import java.util.function.IntPredicate;

import static com.aws.jverify.JVerify.postcondition;
import static com.aws.jverify.JVerify.precondition;

@JVerifyTest(dafnyVerified = 7, dafnyErrors = 0, verifyPrintedDafny = true)
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
}
