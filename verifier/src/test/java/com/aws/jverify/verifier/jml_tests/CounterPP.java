package com.aws.jverify.verifier.jml_tests;
import static com.aws.jverify.JVerify.*;
import com.aws.jverify.*;
import com.aws.jverify.testengine.JVerifyTest;

@JVerifyTest(exitCode = 4, dafnyVerified = 3, dafnyErrors = 2)
public class CounterPP {
    protected /*@ spec_public @*/ int count;
    public void inc() {
        modifies(this);
        count++; // ERROR
//      ^^^^^^^ Error: value does not satisfy the subset constraints of 'int32'
    }
    public void inc2() {
        modifies(this);
        count += 1; // ERROR
//      ^^^^^^^^^^ Error: value does not satisfy the subset constraints of 'int32'
    }
    //@ requires count < Integer.MAX_VALUE;
    public void incOK3() {
        modifies(this);
        precondition(count < Integer.MAX_VALUE);
        count++;
    }
    //@ requires count < Integer.MAX_VALUE;
    public void incOK4() {
        modifies(this);
        precondition(count < Integer.MAX_VALUE);
        count += 1;
    }
    public /*@ pure @*/ int val() {
        return count;
    }
}
