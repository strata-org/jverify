package com.aws.jverify.verifier.tests.javasupport.records;

import com.aws.jverify.Nullable;
import com.aws.jverify.testengine.JVerifyTest;

@JVerifyTest(exitCode = 4, dafnyVerified = 2, dafnyErrors = 1)
public class NullableRecordsVerification {
    record R(int x) {
        void foo() {}
    }
    
    @SuppressWarnings("DataFlowIssue")
    void useRecord(R nonNullR,
                   @Nullable R nullableR) {
        R p;
        if (nullableR == null) {
            p = nonNullR;
            nullableR.foo();
//          ^^^^^^^^^ Error: destructor 'value' can only be applied to datatype values constructed by 'NonNull'
        } else {
            p = nullableR;
            nullableR.foo();
        }
        p.foo();
        @Nullable R nullableP2;
        nullableP2 = p;
    }
}
