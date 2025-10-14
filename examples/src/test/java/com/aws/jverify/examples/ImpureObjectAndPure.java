package com.aws.jverify.examples;

import com.aws.jverify.Impure;
import com.aws.jverify.Pure;

import static com.aws.jverify.JVerify.check;

public class ImpureObjectAndPure {
    @Impure Object createImpureObject() {
        return new Object();
    }
    
    @Pure
    Object createPureObject() {
        return new Object();
    }
    
    @Pure
    @Impure Object returnImpureObject(@Impure Object object) {
        return object;
    }
    
    void test(@Impure Object impure) {
        check(createImpureObject() == createImpureObject());
//                                 ^^ error: check could not be proved 
        check(createPureObject() == createPureObject());
//                               ^^ error: '==' is only allowed when at least one operand's type is impure
        check(returnImpureObject(impure) == returnImpureObject(impure));
    }
}
