package com.aws.jverify.verifier.tests.javasupport.classes;


import com.aws.jverify.Pure;
import com.aws.jverify.testengine.JVerifyTest;

@JVerifyTest(exitCode = 2)
class ClassesAreMutable {
    @Pure
    ClassesAreMutable createC() {
        return new ClassesAreMutable();
//             ^ error: using 'new' in an expression to create an instance of a mutable type is not supported
    }
}
