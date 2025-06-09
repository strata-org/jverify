package com.aws.jverify.verifier.jml_tests;

import com.aws.jverify.*;
import static com.aws.jverify.JVerify.*;
import com.aws.jverify.testengine.JVerifyTest;

@JVerifyTest(exitCode = 0, dafnyVerified = 7, dafnyErrors = 0)
public class CTor {
    private int x;

    public CTor() {
        postcondition(this.x == 42);
        x = 42;
    }

    public CTor(int x_) {
        postcondition(this.x == x_);
        x = x_;
    }

    public CTor(int x_, int y_) {
        precondition(x_+y_<=Integer.MAX_VALUE);
        precondition(x_+y_>=Integer.MIN_VALUE);
        postcondition(this.x == x_+y_);
        x = x_+y_;
    }

    @Pure
    public int get() {
        reads(this);
        return x;
    }

    public static void test1() {
        CTor ct = new CTor();
        int tmp = ct.get();
        check(tmp == 42);
    }


    public static void test2() {
        CTor ct = new CTor(23);
        int tmp = ct.get();
        check(tmp == 23);
    }


    public static void test3() {
        CTor ct = new CTor(23,10);
        int tmp = ct.get();
        check(tmp == 33);
    }
}