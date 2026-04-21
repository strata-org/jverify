package org.strata.jverify.verifier.tests.javasupport.classes;

import org.strata.jverify.testengine.JVerifyTest;

import org.strata.jverify.*;
import static org.strata.jverify.JVerify.*;

// Class that test the support of class allocation and accesses
@JVerifyTest(skip = "Strata: not yet supported", methodsVerified = 5, errorCount = 0)
class Allocate {
    public static IntPair allocateInReturn(int a, int b) {
        postcondition((IntPair p) -> fresh(p) &&  p.getA() == a);
        return new IntPair(a, b);
    }

    public static void test(int a) {
        IntPair p = allocateInReturn(a,2);
        check(p.getA() == a);
    }
}

class IntPair {
    private int a;
    private int b;

    public IntPair(int a_, int b_) {
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
