package com.aws.verifier.examples;

import com.aws.jverify.CheckPreconditionsAtRuntime;
import com.aws.jverify.PreconditionFailure;
import com.aws.jverify.ShouldVerify;

import static com.aws.jverify.JVerify.*;

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
    
    @ShouldVerify
    @CheckPreconditionsAtRuntime
    static int division(int dividend, int divisor) {
        precondition(divisor != 0);
        return dividend / divisor;
    }
}
