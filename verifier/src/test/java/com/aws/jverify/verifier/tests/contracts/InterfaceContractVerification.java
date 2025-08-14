package com.aws.jverify.verifier.tests.contracts;

import com.aws.jverify.Contract;
import com.aws.jverify.ContractException;
import com.aws.jverify.Pure;
import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.check;
import static com.aws.jverify.JVerify.postcondition;

@JVerifyTest(exitCode = 4, dafnyVerified = 9, dafnyErrors = 1)
public class InterfaceContractVerification {
    @SuppressWarnings("UnnecessaryLocalVariable")
    public void root() {
        IImplementer extender = new IImplementer();
        var i = extender.internalContract();
        check(i > 2);
        var e = extender.externalContract(3);
        check(e > 5);
        I base = extender;
        var b = base.externalContract(3);
        check(b > 4);
        var a = extender.addedMethod();
        check(a == 1);
    }


    @Contract(I.class)
    static class IContract implements I {
        @Override
        public int externalContract(int x) {
            postcondition((int r) -> r > 4);
            return 5;
        }
    }

    interface I {

        int externalContract(int x);
        default int internalContract() {
            postcondition((int r) -> r > 2);
//                                   ^^^^^ Related location: this is the postcondition that could not be proved
            return 1;
//          ^^^^^^^^^ Error: a postcondition could not be proved on this return path
        }
    }

    static class IImplementer implements I {
        @Override
        public int externalContract(int x) {
            postcondition((int r) -> r > 5);
            return 6;
        }

        @Pure
        public int addedMethod() {
            return 1;
        }
    }
    
    void noImplementsUser(NoImplements noImplements) {
        var i = noImplements.foo();
        check(i > 4);
    }

    interface NoImplements {
        int foo();
    }
    @Contract(NoImplements.class)
    static class NoImplementsContract {
        int foo() {
            postcondition((int r) -> r > 5);
            throw new ContractException();
        }
    }
}
