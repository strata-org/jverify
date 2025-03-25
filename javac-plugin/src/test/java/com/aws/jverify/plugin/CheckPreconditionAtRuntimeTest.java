package com.aws.jverify.plugin;

import com.aws.jverify.CheckPreconditionsAtRuntime;
import com.aws.jverify.PreconditionFailure;

import static com.aws.jverify.JVerify.precondition;

public class CheckPreconditionAtRuntimeTest {
    public void test() {
        multiplePreconditions(1);
        try {
            multiplePreconditions(0);
            throw new RuntimeException("fail");
        } catch(PreconditionFailure _) {
        }
        noPreconditions(1);
        noStatements(1);
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
