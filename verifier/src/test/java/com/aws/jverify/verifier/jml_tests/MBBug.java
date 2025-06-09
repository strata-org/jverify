package com.aws.jverify.verifier.jml_tests;
import static com.aws.jverify.JVerify.*;
import com.aws.jverify.*;
import com.aws.jverify.testengine.JVerifyTest;

@JVerifyTest(exitCode = 0, dafnyVerified = 1, dafnyErrors = 0)
public class MBBug {

    public MBBug() {
    }

    //@ requires n >= 0;
    //@ measured_by n;
    //@ ensures \result == 0;
    public int zero (int n) {
        precondition(n >= 0);
        postcondition((Integer r) -> r == 0);
        if (n == 0) { return 0; }
        return zero(n-1);
    }
}

// This submission wanted to check measured_by, which is not supported
// yet
