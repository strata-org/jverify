package com.aws.jverify.verifier.jml_tests;
import static com.aws.jverify.JVerify.*;
import com.aws.jverify.*;
import com.aws.jverify.testengine.JVerifyTest;

@JVerifyTest(exitCode = 0, dafnyVerified = 2, dafnyErrors = 0)
public class CallStack {
    
    public void mm() {
        m(-1); // ERROR
//      ^^^^^ Error: a precondition for this call could not be proved
    }
    
    //@ requires x(i);
    public void m(int i) {
        precondition(x(i));
//                   ^^^^ Error: function precondition could not be proved
        
    }
    
    //@ requires pos(i);
    //@ ensures \result == i > 10;
    //@ pure
    @Pure public boolean x(int i) {
        // TODO: postcondition for Pure method
        precondition(pos(i));
        return i > 10;
    }
    
    //@ ensures \result == i > 0;
    //@ pure
    @Pure public boolean pos(int i) {
        // TODO: postcondition for Pure method
        return i > 0;
    }
}