package com.aws.jverify.verifier.tests.verification.externalcontracts;

import com.aws.jverify.Contract;
import com.aws.jverify.ContractException;
import com.aws.jverify.Modifiable;
import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.postcondition;

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
    @Modifiable
//  ^ error: annotation 'Modifiable' on @Contract class 'InterfaceContractErrors$IllegalAnnotationContract' is not allowed, because it must be placed on the contractee
    static class IllegalAnnotationContract implements IllegalAnnotationInterface {
    }

    interface HasGenericArgument<Bar> {}
    @Contract
    static class HasGenericArgumentContract<Foo> implements HasGenericArgument<Foo> {}
//         ^ error: Contract class 'InterfaceContractErrors$HasGenericArgumentContract' has different type parameters than the contractee 'HasGenericArgument'. The parameters must have the same names
}
