package org.strata.jverify.verifier.tests.verification.shouldVerify;

import org.strata.jverify.ContractException;
import org.strata.jverify.Verify;
import org.strata.jverify.testengine.JVerifyTest;
import java.beans.IntrospectionException;
import java.util.List;

import static org.strata.jverify.JVerify.postcondition;

@JVerifyTest(skip = "Strata: not yet supported", methodsVerified = 9, errorCount = 0)
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
    
    void handleIntersectionTypes() {
        var intersectionTypeList = listOf(new C(), new D());
    }
    
    interface A {}
    interface B {}
    record C() implements A, B {}
    record D() implements A, B {}

    @Verify(false)
    static <E> List<E> listOf(E e1, E e2) {
        throw new ContractException();
    }
}
