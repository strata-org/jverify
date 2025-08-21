package com.aws.jverify.verifier.tests.verification;

import com.aws.jverify.Pure;
import com.aws.jverify.testengine.JVerifyTest;
import java.util.List;

@JVerifyTest(dafnyVerified = 6, dafnyErrors = 0)
public class MissingContracts {
    interface MissingContract {
        int assumedPure();
        void cantBePure();
    }
    
//    @Pure
//    int pureUser(MissingContract missingContract) {
//        // missingContract.cantBePure();
//        return missingContract.assumedPure();
//    }


    @SuppressWarnings("UnnecessaryBoxing")
    Integer pureUser3() {
        return Integer.valueOf(3);
//             ^ warning: missing contract for method 'valueOf' in class 'java.lang.Integer'
    }
    
    @Pure
    List<Value> pureUser2(List<Value> values) {
        return List.of(new Value());
//             ^ warning: missing contract for method 'of' in class 'java.util.List'
    }
    
    void impureUser(List<Value> values) {
        values.addFirst(new Value());
//      ^ warning: missing contract for method 'addFirst' in class 'java.util.List'
    }
    record Value() {}
}
