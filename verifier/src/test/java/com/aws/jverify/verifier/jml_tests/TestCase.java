package com.aws.jverify.verifier.jml_tests;
import static com.aws.jverify.JVerify.*;
import com.aws.jverify.*;
import com.aws.jverify.testengine.JVerifyTest;

@JVerifyTest(exitCode = 0, dafnyVerified = 1, dafnyErrors = 0)
public class TestCase {
    public int a = 1;

    //@ requires b >= +a;
    public void plus(int b) {
        precondition(b >= +a);
    }
}