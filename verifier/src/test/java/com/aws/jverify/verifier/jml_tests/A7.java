/**
 * 
 * Category: Non-interprocedural, loop-free
 * Features: locals, primatives, fields
 * 
 * @author jls
 *
 */
package com.aws.jverify.verifier.jml_tests;

import com.aws.jverify.testengine.JVerifyTest;
import com.aws.jverify.*;
import static com.aws.jverify.JVerify.*;

@JVerifyTest(exitCode = 0, dafnyVerified = 1, dafnyErrors = 0)
public class A7 {
    
    int THE_FIELD;
    
    //@ requires true;
    public int localTest(int a, int b){
        modifies(this);
        
        THE_FIELD = 999;

        if(THE_FIELD > -1){
            return 0;
        }
        
        THE_FIELD = 777;
        
        return -1;
    }
}

