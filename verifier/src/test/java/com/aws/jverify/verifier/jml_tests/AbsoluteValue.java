package com.aws.jverify.verifier.jml_tests;
import static com.aws.jverify.JVerify.*;
import com.aws.jverify.*;
import com.aws.jverify.testengine.JVerifyTest;

@JVerifyTest(exitCode = 0, dafnyVerified = 2, dafnyErrors = 0)
public class AbsoluteValue{
	public int IntAbsolute(int num){
		precondition(num >= 0 || (num < 0 && num > Integer.MIN_VALUE));
		postcondition((Integer result) -> 
			 num >= 0 ? result == num && result >= 0 : result == -num && result >= 0);
		
		
		if(num >= 0)
			return num;
		else
			return -num;
	}

	public long LongAbsolute(long num){
		precondition(num >= 0 || (num < 0 && num > Long.MIN_VALUE));
		postcondition((Long result) -> 
			 num >= 0 ? result == num && result >= 0 : result == -num && result >= 0);
		if(num >= 0)
			return num;
		else
			return -num;	
	}
}
