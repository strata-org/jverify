/**
 * 
 * Category: Non-interprocedural, loop-free
 * Features: locals, primatives, fields in branches, fields. 
 * 
 * @author jls
 *
 */
package com.aws.jverify.verifier.jml_tests;
import com.aws.jverify.testengine.JVerifyTest;
import static com.aws.jverify.JVerify.*;

@JVerifyTest(exitCode = 0, dafnyVerified = 1, dafnyErrors = 0)
public class A5 {
    
    int THE_FIELD;
    
    //@ requires true;
    public int localTest(int a, int b){
        modifies(this);
        
        if(THE_FIELD > -1){
            return 0;
        }
        
        THE_FIELD = 100;
        return -1;
    }
    
    
}

