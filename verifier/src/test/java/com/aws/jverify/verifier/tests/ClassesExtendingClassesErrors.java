package com.aws.jverify.verifier.tests;

import com.aws.jverify.Pure;
import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.*;

@JVerifyTest(exitCode = 2)
public class ClassesExtendingClassesErrors {}

class ExtenderErrors extends ExtendeeErrors {
    
    @Pure
    @Override
    public int pureVirtual(int input) {
//             ^^^^^^^^^^^ Error: fully defined function 'pureVirtual' is inherited from trait 'ExtendeeErrors' and is not allowed to be re-declared
 
        return 3;
    }
    
    @Override
    public int impureVirtual() {
        postcondition((Integer r) -> r >= 20);
        return 20;
    }
} 


abstract class ExtendeeErrors {
    
    @Pure
    public int pureVirtual(int input) {
        return 3;
    }
    
    public int impureVirtual() {
        postcondition((Integer r) -> r >= 30);
        return 40;
    }
}
