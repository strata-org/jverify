package org.strata.jverify.verifier.tests.javasupport.records;

import org.strata.jverify.Nullable;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.precondition;

@JVerifyTest(skip = "Strata: not yet supported", exitCode = 4, methodsVerified = 4, errorCount = 1)
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
    
    String sumNullableString(@Nullable String s) {
        precondition(s != null);
        return "hello" + s;
    }
}
