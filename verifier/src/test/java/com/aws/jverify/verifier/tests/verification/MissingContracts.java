package com.aws.jverify.verifier.tests.verification;

import com.aws.jverify.Pure;
import com.aws.jverify.testengine.JVerifyTest;
import com.aws.jverify.testing.GenericParent;

import java.util.ArrayList;
import java.util.List;

import static com.aws.jverify.JVerify.check;

@JVerifyTest(exitCode = 4, javaVerified = 6, javaErrors = 1)
public class MissingContracts {
    
    void checkFalse() {
        check(false);
//      ^^^^^^^^^^^^ Error: assertion could not be proved
    }
    
    interface MissingContract {
        int assumedPure();
        void cantBePure();
    }
    
    @Pure
    int pureUser(MissingContract missingContract) {
        return missingContract.assumedPure();
//             ^ warning: missing contract for method 'assumedPure' in class 'com.aws.jverify.verifier.tests.verification.MissingContracts.MissingContract'
    }

    @SuppressWarnings("UnnecessaryBoxing")
    @Pure
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
        // By creating a contract for addFirst (which is in List) and a contract for ArrayList,
        // we test that missing contracts created in an inheritance chain
        // do not cause issues.
        values.addFirst(new Value());
//      ^ warning: missing contract for method 'addFirst' in class 'java.util.List'
        var l = new ArrayList<Value>();
//              ^ warning: missing contract for method '<init>' in class 'java.util.ArrayList'
    }
    record Value() {}
    
    void referenceTwoMissingClassesWithTheSameName() {
        com.aws.jverify.testing.a.DuplicateName duplicateNameA;
        com.aws.jverify.testing.b.DuplicateName duplicateNameB;
    }
    
    void indirectReference(GenericParent gp) {}
    
// TODO generate ghost code everywhere
//    void missingField() {
//        var zero = BigDecimal.ZERO;
//    }
    
}
