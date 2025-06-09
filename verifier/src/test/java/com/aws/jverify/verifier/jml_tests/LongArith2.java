package com.aws.jverify.verifier.jml_tests;
import static com.aws.jverify.JVerify.*;
import com.aws.jverify.*;
import com.aws.jverify.testengine.JVerifyTest;

@JVerifyTest(exitCode = 0, dafnyVerified = 5, dafnyErrors = 0)
public class LongArith2 {

    /*@ spec_public @*/ private long ub,lb;
    //@ public invariant Integer.MIN_VALUE <= lb && lb <= Integer.MAX_VALUE;
    //@ public invariant Integer.MIN_VALUE <= ub+1 && ub <= Integer.MAX_VALUE;
    
    @Pure
    @Invariant
    private boolean invariant() {
        reads(this);
        return Integer.MIN_VALUE <= lb && lb <= Integer.MAX_VALUE && Integer.MIN_VALUE <= ub+1 && ub <= Integer.MAX_VALUE;
    }

    //@ public normal_behavior
    //@   requires l <= ((long)u)+1;
    //@   assignable \nothing;
    //@   ensures lb == (long)l && ub == (long)u;
    public LongArith2(int l, int u) {
        precondition(((long)l) <= ((long)u)+1);
        postcondition(((long)l) == lb && ((long)u) == ub);
        lb = l; ub = u; 
    }

    //@ ensures \result == ub;
    public /*@ pure @*/ long upperBound() {
        postcondition((Long r) -> r == ub);
        return ub;
    }

    //@ ensures \result == ub - lb - 1;
    public /*@ pure @*/ long size() {
        postcondition((Long r) -> r == ub - lb - 1);
        return ub - lb - 1;
    }
}
