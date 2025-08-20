package com.aws.jverify.verifier.tests.verification;

import com.aws.jverify.Pure;
import com.aws.jverify.testengine.JVerifyTest;

import java.util.function.IntPredicate;

import static com.aws.jverify.JVerify.postcondition;

@JVerifyTest(exitCode = 0, dafnyVerified = 6, dafnyErrors = 0, verifyPrintedDafny = true)
public class MethodContractsVerification {
    
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

    int postconditionNameClash(int x) {
        postcondition((int res) -> res == x);
        int res;
        res = x;
        return res;
    }
}
