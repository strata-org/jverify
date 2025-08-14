package com.aws.jverify.verifier.tests.verification;


import com.aws.jverify.Pure;
import com.aws.jverify.testengine.JVerifyTest;

import java.util.List;

@JVerifyTest(dafnyVerified = 7, dafnyErrors = 0)
public class MissingContracts {
    interface MissingContract {
        int assumedPure();
        void cantBePure();
    }
    
    @Pure
    int pureUser(MissingContract missingContract) {
        missingContract.cantBePure();
        return missingContract.assumedPure();
    }

    record Value() {}
    @Pure
    List<Value> pureUser2(List<Value> values) {
        var result = List.of(new Value());
        result.add(new Value());
        return result;
    }
}
