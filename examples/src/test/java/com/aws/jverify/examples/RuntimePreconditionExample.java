package com.aws.jverify.examples;

import com.aws.jverify.CheckPreconditionsAtRuntime;
import com.aws.jverify.PreconditionFailure;
import com.aws.jverify.Verify;

import static com.aws.jverify.JVerify.*;

// Skipping because we don't translate try-catch yet
// @JVerifyTest
public class RuntimePreconditionExample {
    public static void main(String[] args) {
        division(3, 1);
        try {
            division(3, 0);
            System.exit(2);
        } catch(PreconditionFailure e) {
            System.exit(0);
        }
    }
    
    @Verify
    @CheckPreconditionsAtRuntime
    static int division(int dividend, int divisor) {
        precondition(divisor != 0);
        return dividend / divisor;
    }
}
