package com.aws.jverify.verifier.jml_tests;
import static com.aws.jverify.JVerify.*;
import com.aws.jverify.*;
import com.aws.jverify.testengine.JVerifyTest;

@JVerifyTest(exitCode = 0, dafnyVerified = 2, dafnyErrors = 0)
public class Unreachable {
    public void assertFalse() {
        if (true) {
            // hi!
        } else {
            //@ assert false;
            check(false);
        }
    }

    public void unreachable() {
        if (true) {
            // hi!
        } else {
            //@ unreachable;
            check(false);
        }
    }
}