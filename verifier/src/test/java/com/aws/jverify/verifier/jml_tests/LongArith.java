package com.aws.jverify.verifier.jml_tests;
import static com.aws.jverify.JVerify.*;
import com.aws.jverify.*;
import com.aws.jverify.testengine.JVerifyTest;

@JVerifyTest(exitCode = 0, dafnyVerified = 4, dafnyErrors = 0)
public class LongArith {

    /*@ spec_public @*/ private long lb;
    /*@ spec_public @*/ private int ib;
    //@ public invariant Integer.MIN_VALUE <= lb && lb <= Integer.MAX_VALUE;
    
    @Pure
    @Invariant
    private boolean invariant() {
        reads(this);
        return Integer.MIN_VALUE <= lb && lb <= Integer.MAX_VALUE;
    }

    public LongArith(int l) {
        lb = (long)l;
        ib = l;
    }

    //@ requires i < Integer.MAX_VALUE;
    public void use_int(int i) {
        modifies(this);
        precondition(i < Integer.MAX_VALUE);
        if (!(ib <= i)) { return; }
        //@ assert ib <= i;
        check(ib <= i);
        ib = i+1;
        //@ assert ib == i+1;
        check(ib == i+1);
        //@ assert ib > i;
        check(ib > i);
    }

    //@ requires il < Integer.MAX_VALUE;
    public void use_long(long il) {
        modifies(this);
        precondition(il < Integer.MAX_VALUE);
        if (!(lb <= il)) { return; }
        //@ assert lb <= il;
        check(lb <= il);
        lb = il+1;
        //@ assert lb == il+1;
        check(lb == il+1);
        //@ assert lb > il;
        check(lb > il);
    }
}
