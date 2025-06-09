package com.aws.jverify.verifier.jml_tests;


import com.aws.jverify.*;
import static com.aws.jverify.JVerify.*;
import com.aws.jverify.testengine.JVerifyTest;

@JVerifyTest(exitCode = 0, dafnyVerified = 2, dafnyErrors = 0)
public class A15 {

    public int FIELD;
    
    //@ requires true;
    //@ ensures this.FIELD==3;
    //@ ensures \result == a + b;
    //@ assignable this.FIELD;
    public int add(int a, int b){
        precondition(a+b < Integer.MAX_VALUE && a+b>Integer.MIN_VALUE);
        modifies(this);
        postcondition((Integer result) -> result == a + b);
        postcondition(this.FIELD == 3);
	this.FIELD = 3;
	return a+b;
    }
    
    //@ requires true;
    public int localTest(int a, int b){
        precondition(a+b < Integer.MAX_VALUE && a+b>Integer.MIN_VALUE);
        modifies(this);
        postcondition((Integer result) -> result == a + b);
	return add(a, b);
    }
    

    
}
