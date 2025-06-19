package com.aws.jverify.verifier.tests;

import com.aws.jverify.Pure;
import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.postcondition;

@JVerifyTest(dafnyVerified = 3, dafnyErrors = 0)
public class MethodContractsVerification {
    
    public int methodReferencePostCondition() {
        postcondition(MethodContractsVerification::isEven);
        
        // Include lambda based post-condition because it introduces a return value name
        postcondition((Integer r) -> r == 2);
        
        return 2;
    }

    public int differentReturnValueNames() {
        postcondition((Integer r) -> r > 1);
        postcondition((Integer v) -> v < 3);

        return 2;
    }

    @Pure
    public static boolean isEven(int x) {
        return x % 2 == 0;
    }

    int postconditionNameClash(int x) {
        postcondition((Integer res) -> res == x);
        int res;
        res = x;
        return res;
    }
}
