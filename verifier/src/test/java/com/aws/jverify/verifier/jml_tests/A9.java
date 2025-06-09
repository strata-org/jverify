package com.aws.jverify.verifier.jml_tests;
/**
 * 
 * Category: Non-interprocedural, loop-free
 * Features: locals, primatives, fields
 * 
 * @author jls
 *
 */

import com.aws.jverify.testengine.JVerifyTest;

@JVerifyTest(exitCode = 0, dafnyVerified = 1, dafnyErrors = 0)
public class A9 {
    

    //@ requires true;
    public int localTest0(){
        
        int a;
        
        a = 3;
        a = 4;
        a = 5;
        
        return -1;
    }


}

