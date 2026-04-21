package org.strata.jverify.examples;

import org.strata.jverify.CheckPreconditionsAtRuntime;
import org.strata.jverify.PreconditionFailure;
import org.strata.jverify.Verify;

import static org.strata.jverify.JVerify.*;

//@JVerifyTest(skip = "Skipping because we don't translate try-catch yet")
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
