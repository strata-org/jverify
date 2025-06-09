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

@JVerifyTest(exitCode = 0, dafnyVerified = 1, dafnyErrors = 0)
public class A12 {
    
    int THE_FIELD;
    
    //@ requires a > 100;
    public int localTest(int a, int b, A12 aa){
        precondition(a > 100);
        modifies(aa);
        modifies(this);
        
        aa.THE_FIELD = 0;
        
        if(THE_FIELD > -1){
            return 0;
        }
        
        THE_FIELD = 100;
        return -1;
    }
    
    
}

