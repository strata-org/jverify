package org.strata.jverify.verifier.tests.verification.externalcontracts;

import org.strata.jverify.Contract;
import org.strata.jverify.ContractException;
import org.strata.jverify.Impure;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.postcondition;

@JVerifyTest(exitCode = 2)
public class InterfaceContractErrors {
    @Contract(I.class)
    static class IContract implements I {

        @Override
        public int redeclaredContract() {
//                 ^ error: method 'redeclaredContract' already has an internally defined contract
            postcondition((int r) -> true);
            throw new ContractException();
        }
    }

    interface I {
        default int redeclaredContract() {
            postcondition((int r) -> r > 2);
            return 1;
        }
    }

    interface IllegalAnnotationInterface {}

    @Contract
    @Impure
//  ^ error: annotation 'Impure' on @Contract class 'IllegalAnnotationContract' is not allowed, because it must be placed on the contractee
    static class IllegalAnnotationContract implements IllegalAnnotationInterface {
    }

    interface HasGenericArgument<Bar> {}
    @Contract
    static class HasGenericArgumentContract<Foo> implements HasGenericArgument<Foo> {}
//         ^ error: Contract class 'HasGenericArgumentContract' has different type parameters than the contractee 'HasGenericArgument'. The parameters must have the same names
}
