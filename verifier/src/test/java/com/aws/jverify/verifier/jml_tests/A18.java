package com.aws.jverify.verifier.jml_tests;

import com.aws.jverify.testengine.JVerifyTest;
import com.aws.jverify.*;
import static com.aws.jverify.JVerify.*;

@JVerifyTest(exitCode = 0, dafnyVerified = 1, dafnyErrors = 1)
public class A18 {

    public int FIELD;
    
    //@ requires true;
    //@ ensures this.FIELD==3;
    //@ ensures \result == a + b;
    //@ assignable this.FIELD;
    public int add(int a, int b){
        precondition(a+b<Integer.MAX_VALUE && a+b>Integer.MIN_VALUE);
        modifies(this);
        postcondition((Integer result) -> result == a + b && this.FIELD == 3);
	this.FIELD = 3;
	return a+b;
    }
    
   
   //@ requires true; 
   public int localTest2(int a, int b){
       precondition(a+b<Integer.MAX_VALUE && a+b>Integer.MIN_VALUE);
        modifies(this);
       int tmp = add(a, b);

	return tmp * this.FIELD;
//             ^^^^^^^^^^^^^^^^^^^^^^^^^ Error: value does not satisfy the subset constraints of 'int32'
   }
    
    

    
}
