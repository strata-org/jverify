package com.aws.jverify.verifier.jml_tests;

import com.aws.jverify.*;
import static com.aws.jverify.JVerify.*;
import com.aws.jverify.testengine.JVerifyTest;

@JVerifyTest(exitCode = 0, dafnyVerified = 1, dafnyErrors = 0)
public class A16 {
    
    public int iii;
    
    public int set(int k) {
        modifies(this);
        int j = iii;
        iii = k;
        return j;
    }

    
    
}