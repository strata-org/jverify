package com.aws.jverify.verifier.jml_tests;

import static com.aws.jverify.JVerify.*;
import com.aws.jverify.*;
import com.aws.jverify.testengine.JVerifyTest;

@JVerifyTest(exitCode = 4, dafnyVerified = 4, dafnyErrors = 1)
public class RecursiveInvariant {
    
    //@ public invariant m();
    @Invariant
    @Pure
    public /*@ pure */ boolean m() { return true; } // ERROR
//                                 ^^^ Error: cannot prove termination; try supplying a decreases clause
    
    //@ requires m();
    public void t() {
        precondition(m());
    }
}