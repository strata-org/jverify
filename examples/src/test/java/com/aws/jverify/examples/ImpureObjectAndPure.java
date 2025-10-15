package com.aws.jverify.examples;

import com.aws.jverify.Impure;
import com.aws.jverify.Pure;

import static com.aws.jverify.JVerify.check;

public class ImpureObjectAndPure {
    @Pure
    @Impure Object purelyCreateImpureObject() {
        return new Object();
//             ^ error: using 'new' in a pure expression to create an instance of an impure type is not allowed
    }
    
    @Impure Object impureMethod() {
        return new Object();
    }
    
    record PureType() {}
    @Pure
    PureType createPureType() {
        return new PureType();
    }
    
    void foo(@Impure Object impure) {
        check(impureMethod() == impureMethod());
//                           ^^ error: check could not be proved 
        check(createPureType() == createPureType());
//                             ^^ error: '==' is only allowed when at least one operand's type is impure
    }
}
