package com.aws.jverify.verifier.tests.strata;

import com.aws.jverify.testengine.JVerifyTest;
import static com.aws.jverify.JVerify.*;
import static com.aws.jverify.verifier.Backend.Strata;

@JVerifyTest(exitCode = 0, methodsVerified = 2, errorCount = 0, BACKENDS = {Strata})
class ArrayBasic {
    static int getFirst(int[] arr) {
        precondition(arr.length > 0);
        postcondition((int res) -> res == arr[0]);
        return arr[0];
    }
}
