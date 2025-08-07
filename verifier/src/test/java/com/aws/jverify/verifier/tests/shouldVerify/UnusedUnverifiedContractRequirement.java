package com.aws.jverify.verifier.tests.shouldVerify;

import com.aws.jverify.Verify;
import com.aws.jverify.testengine.JVerifyTest;
import java.beans.IntrospectionException;

import static com.aws.jverify.JVerify.postcondition;

@JVerifyTest(dafnyVerified = 10, dafnyErrors = 0)
public class UnusedUnverifiedContractRequirement {

    @Verify(false)
    void unusedAndUnverifiedIsValid(IntrospectionException introspectionException) {}

    void typeWithoutContractHasCorrectInheritance(IntrospectionException introspectionException) {
        Exception exception = introspectionException;
    }

    void testInheritance(Base base) {
        base.baseIsUsed();
    }

    static class Base {
        void baseIsUsed() {

        }
    }

    static class Extendee extends Base {
        @SuppressWarnings("ConstantValue")
        @Override
        @Verify(false)
        void baseIsUsed() {
            postcondition(((Object)this) instanceof IntrospectionException);
        }
    }
}
