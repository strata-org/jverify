package com.aws.jverify.verifier.jml_tests;

/**
 * 
 * Category: Non-interprocedural, loop-free
 * Features: locals, primatives, fields in branches, fields. 
 * 
 * @author jls
 *
 */


import com.aws.jverify.*;
import static com.aws.jverify.JVerify.*;

import com.aws.jverify.testengine.JVerifyTest;

@JVerifyTest(exitCode = 0, dafnyVerified = 2, dafnyErrors = 0)
public class A13 {
    int THE_FIELD;
    
    public @Nullable A13 tricky;  // Adding @Nullable to account for default constructor

    //@ requires true;
    public int localTest(int a, int b, A13 aa){
        precondition(aa.tricky != null);
        precondition(aa.tricky.tricky != null);
        modifies(aa.tricky.tricky);
        modifies(this);
        
        THE_FIELD = 0;
        
        aa.tricky.tricky.THE_FIELD = 100;
        
        return -1;
    }
    
    
}

