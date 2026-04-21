package org.strata.jverify.verifier.tests.javasupport.classes;

import org.strata.jverify.Pure;
import org.strata.jverify.testengine.JVerifyTest;

@JVerifyTest(skip = "Strata: not yet supported", exitCode = 22)
public class ClassesExtendingClassesErrors {}

class ExtenderErrors extends ExtendeeErrors {
    
    @Pure
    @Override
    public int pureVirtual(int input) {
//             ^^^^^^^^^^^ Error: fully defined function 'pureVirtual' is inherited from trait 'ExtendeeErrors' and is not allowed to be re-declared
 
        return 3;
    }
}


abstract class ExtendeeErrors {
    
    @Pure
    public int pureVirtual(int input) {
        return 3;
    }
}
