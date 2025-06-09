package com.aws.jverify.verifier.jml_tests;

import com.aws.jverify.*;
import static com.aws.jverify.JVerify.*;
import com.aws.jverify.testengine.JVerifyTest;

@JVerifyTest(exitCode = 0, dafnyVerified = 3, dafnyErrors = 0)
public class A17 {

    public int FIELD;
    
    //@ requires true;
    //@ ensures this.FIELD==3;
    //@ ensures \result == a + b;
    //@ assignable this.FIELD;
    public int add(int a, int b){
        precondition(true);
        precondition(a+b<Integer.MAX_VALUE && a+b>Integer.MIN_VALUE);
        postcondition((Integer result) -> result == a + b && this.FIELD == 3);
        modifies(this);
	this.FIELD = 3;
	return a+b;
    }
    
    
    //@ requires true;
    public int localTest3(int a, int b){
        modifies(this);
        precondition(a+b<Integer.MAX_VALUE/100 && a+b>Integer.MIN_VALUE/50);

        int tmp = add(a, b);
	
	if(tmp > 100){
	    return tmp * 100;
	}else{
	    return tmp * 50;
	}
	
    }
   

    
}
