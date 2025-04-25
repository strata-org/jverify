package com.aws.jverify.plugin;

import com.aws.jverify.CheckPreconditionsAtRuntime;
import com.aws.jverify.PreconditionFailure;

import static com.aws.jverify.JVerify.precondition;

public class CheckPreconditionAtRuntimeTest {
    public static void main(String[] args) {
        happyCase(1);
        try {
            happyCase(0);
            System.exit(1);
        } catch(PreconditionFailure _) {
        }
        multiplePreconditions(1);
        try {
            multiplePreconditions(0);
            System.exit(1);
        } catch(PreconditionFailure _) {
        }
        noPreconditions(1);
        noStatements(1);
        System.exit(0);
    }

    @CheckPreconditionsAtRuntime
    static int happyCase(int input) {
        precondition(input != 0);
        return input;
    }
    
    @CheckPreconditionsAtRuntime
    static int multiplePreconditions(int input) {
        precondition(true);
        precondition(input != 0);
        return input;
    }

    @CheckPreconditionsAtRuntime
    static int noPreconditions(int input) {
        return input;
    }

    @CheckPreconditionsAtRuntime
    static void noStatements(int input) {
    }
}
