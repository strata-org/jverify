package com.aws.jverify.verifier.jml_tests;
import static com.aws.jverify.JVerify.*;
import com.aws.jverify.*;
import com.aws.jverify.testengine.JVerifyTest;

@JVerifyTest(exitCode = 0, dafnyVerified = 2, dafnyErrors = 0)
public class Opt {

//@ public normal_behavior
//@   requires i >= 42;
//@   ensures \result == 420;
public int n(int i) { 
   precondition(i >= 42);
   postcondition((Integer r) -> r == 420);
   return 420; 
}

public int m(int i) {
   precondition(i >= 42);
   return n(i);

}
}
