// TEST: exitCode=0 dafnyVerified=5 dafnyErrors=0

package com.aws.jverify.verifier.tests;

import com.aws.jverify.testengine.JVerifyTest;

import com.aws.jverify.*;
import static com.aws.jverify.JVerify.*;

class Point {
    private int a;
    private int b;

    public Point(int a_, int b_) {
        postcondition(this.a == a_);
        postcondition(this.b == b_);
        this.a = a_;
        this.b = b_;
    }

    @Pure
    public int getA() {
        reads(this);
        return a;
    }
}

// Class that test the support of array allocation and accesses
@JVerifyTest
class Allocate {

    public static Point allocateInReturn(int a, int b) {
        postcondition((Point p) -> fresh(p) &&  p.getA() == a);
        return new Point(a, b);
    }

    public static void test(int a) {
        Point p = allocateInReturn(a,2);
        check(p.getA() == a);
    }

}