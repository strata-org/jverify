package com.aws.jverify.verifier.jml_tests;
import static com.aws.jverify.JVerify.*;
import com.aws.jverify.*;

import com.aws.jverify.testengine.JVerifyTest;

@JVerifyTest(exitCode = 0, dafnyVerified = 1, dafnyErrors = 0)
public class E1 {
    
    //@ requires true;
    public static int m0(int a, int b){
	precondition(a+b<Integer.MAX_VALUE && a+b>Integer.MIN_VALUE && a-b<Integer.MAX_VALUE && a-b>Integer.MIN_VALUE);
	if(a < 0 || b < 0 || a + b < 10 || a - b < 10 || a * b < 10){
	    return a - b;
	}

	return a + b;	
    }
}
