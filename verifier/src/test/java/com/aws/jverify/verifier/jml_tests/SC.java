package com.aws.jverify.verifier.jml_tests;

import static com.aws.jverify.JVerify.*;
import com.aws.jverify.*;
import com.aws.jverify.testengine.JVerifyTest;

@JVerifyTest(exitCode = 0, dafnyVerified = 2, dafnyErrors = 0)
public class SC {
	//@ ensures a < 0 ==> \result == 1;
	//@ ensures b < 0 ==> \result == 1;
	//@ ensures a + b < 0 ==> \result == 1;
	//@ ensures a >= 0 && b >= 0 && a+b >= 10 ==> \result == 2;
	public int m(int a, int b) {
		postcondition((Integer r) -> a < 0 || b < 0 || a + b < 10 ? r == 1 : r == 2);
		if(a < 0 || b < 0 || a + b < 10){
		    return 1;
		}
		return 2;

	}

	//@ ensures a < 0 ==> \result == 2;
	//@ ensures b < 0 ==> \result == 2;
	//@ ensures a + b < 0 ==> \result == 2;
	//@ ensures a >= 0 && b >= 0 && a+b >= 10 ==> \result == 1;
	public int mm(int a, int b) {
		postcondition((Integer r) -> a < 0 || b < 0 || a + b < 10 ? r == 2 : r == 1);
		if(a >= 0 && b >= 0 && a + b >= 10){
		    return 1;
		}
		return 2;

	}

}
