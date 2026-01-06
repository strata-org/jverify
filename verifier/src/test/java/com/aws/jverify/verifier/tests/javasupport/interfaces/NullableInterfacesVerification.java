package com.aws.jverify.verifier.tests.javasupport.interfaces;

import com.aws.jverify.Impure;
import com.aws.jverify.Nullable;
import com.aws.jverify.testengine.JVerifyTest;

@JVerifyTest(exitCode = 4, methodsVerified = 3, errorCount = 2)
public class NullableInterfacesVerification {
    interface PureInterface {
        default void foo() {}
    }
    
    @SuppressWarnings("DataFlowIssue")
    void usePureInterface(PureInterface nonNullP,
                          @Nullable PureInterface nullableP) {
        PureInterface p;
        if (nullableP == null) {
            p = nonNullP;
            nullableP.foo();
//          ^^^^^^^^^ Error: destructor 'value' can only be applied to datatype values constructed by 'NonNull'
        } else {
            p = nullableP;
            nullableP.foo();
        }
        p.foo();
        @Nullable PureInterface nullableP2;
        nullableP2 = p;
    }
    
    @Impure
    interface ImpureInterface {
        default void foo() {}
    }

    @SuppressWarnings("DataFlowIssue")
    void useImpureInterface(ImpureInterface p,
                            @Nullable ImpureInterface nullableP) {
        p.foo();
        if (nullableP == null) {
            nullableP.foo();
//          ^^^^^^^^^ Error: target object could not be proved to be non-null
        } else {
            nullableP.foo();
        }
        @Nullable ImpureInterface nullableQ = p;
    }
}
