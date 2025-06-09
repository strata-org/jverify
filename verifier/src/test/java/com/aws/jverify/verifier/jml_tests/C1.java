package com.aws.jverify.verifier.jml_tests;

import static com.aws.jverify.JVerify.*;
import com.aws.jverify.*;

import com.aws.jverify.testengine.JVerifyTest;

@JVerifyTest(exitCode = 0, dafnyVerified = 2, dafnyErrors = 0)
public class C1 {

    
    //@ requires ar!=null;
    public static int sum(int ar[]){
//		precondition(ar!=null); TODO: arrays should be nullable - this precondition currently adds a warning
		precondition(ar.length < Integer.MAX_VALUE/2);
	int sum = 0;
	/*@ 
          @ maintaining -1 < i && i <= ar.length;
          @ maintaining sum == i;
          @ decreasing ar.length - i;
          @*/
	for(int i=0; i < ar.length; i++){
		invariant(-1 < i && i <= ar.length);
		invariant(sum == i);
		decreases(ar.length - i);
	    sum += 1;
	    int tmp = sum;

		for(int j=0; j < ar.length; j++){ // an extra loop, just to be tricky
			invariant(-1 < j && j <= ar.length);
			invariant(sum <= j + i + 1);
			sum++;
	    }
	    
	    sum=tmp;
	}
	
	//sum = 36;
	
	return sum;
    }
}
