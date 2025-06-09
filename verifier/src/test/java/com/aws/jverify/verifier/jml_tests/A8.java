package com.aws.jverify.verifier.jml_tests;
/**
 * 
 * Category: Non-interprocedural, loop-free
 * Features: locals, primatives, fields
 * 
 * @author jls
 *
 */


import com.aws.jverify.*;
import static com.aws.jverify.JVerify.*;
import com.aws.jverify.testengine.JVerifyTest;

@JVerifyTest(exitCode = 0, dafnyVerified = 1, dafnyErrors = 0)
public class A8 {
    
    int THE_FIELD;
    
    //@ requires true;
    public int localTest(int a, int b){
        modifies(this);
        
        THE_FIELD = 999;

        if(a > -1){
            return 0;
        }else{
            if(a > -2){
                THE_FIELD = 888;
                return 100;
            }
        }
        
        THE_FIELD = 777;
        return -1;
    }
    
    
}

