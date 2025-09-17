package com.aws.jverify.verifier.tests.javasupport.interfaces;

import com.aws.jverify.Contract;
import com.aws.jverify.Modifiable;
import com.aws.jverify.Nullable;
import com.aws.jverify.testengine.JVerifyTest;

@JVerifyTest(exitCode = 4, dafnyVerified = 23, dafnyErrors = 5)
public class NullableInterfacesVerification {
    interface PureInterface {
        default void foo() {}
    }
    
    @SuppressWarnings("DataFlowIssue")
    void usePureInterface(PureInterface p,
                          @Nullable PureInterface nullableP) {
        p.foo();
        if (nullableP == null) {
            nullableP.foo();
        } else {
            nullableP.foo();
        }
    }
    
    @Modifiable
    interface ImpureInterface {
        default void foo() {}
    }

    @SuppressWarnings("DataFlowIssue")
    void useImpureInterface(ImpureInterface p,
                            @Nullable ImpureInterface nullableP) {
        p.foo();
        if (nullableP == null) {
            nullableP.foo();
        } else {
            nullableP.foo();
        }
    }
}
