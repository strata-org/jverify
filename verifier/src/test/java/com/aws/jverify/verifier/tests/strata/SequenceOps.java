package com.aws.jverify.verifier.tests.strata;

import com.aws.jverify.testengine.JVerifyTest;
import static com.aws.jverify.JVerify.*;
import static com.aws.jverify.verifier.Backend.Strata;

@JVerifyTest(exitCode = 0, methodsVerified = 2, errorCount = 0, BACKENDS = {Strata})
class SequenceOps {
    static void testContains(int[] arr, int x) {
        precondition(arr.length > 0);
        precondition(arr[0] == x);
        check(sequence(arr).contains(x));
    }
}
