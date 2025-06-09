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


public class A11 {
    
    /*@ spec_public */ int THE_FIELD;
    
    //@ requires true;
    public int localTest(int a, int b){
        
        THE_FIELD = 999;

        if(a > -1){
            return 0;
        }
        
        THE_FIELD = 777;
        return -1;
    }
    

     

}

