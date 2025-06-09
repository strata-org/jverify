package com.aws.jverify.verifier.jml_tests;
import static com.aws.jverify.JVerify.*;
import com.aws.jverify.*;
import com.aws.jverify.testengine.JVerifyTest;

@JVerifyTest(exitCode = 0, dafnyVerified = 2, dafnyErrors = 0)
class SITA3{
   /*@ public normal_behaviour
     @  ensures  
     @    a[pos1]  == \old(a[pos2]) &&
     @    a[pos2]  == \old(a[pos1]);
     @*/
    public void  swap(int[] a,int pos1, int pos2) {
        precondition(0 <= pos1 && pos1 < a.length && 0 <= pos2 && pos2 < a.length);
        modifies(a);
        postcondition(a[pos1] == old(a[pos2]) && a[pos2] == old(a[pos1]));
        postcondition(forall((Integer i) -> i<0 || i>= a.length || i==pos1 || i==pos2 || a[i] == old(a[i])));
	int temp;
        temp = a[pos1]; a[pos1] = a[pos2] ; a[pos2] = temp;
    }
}