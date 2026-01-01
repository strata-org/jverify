package com.aws.jverify.verifier.tests.verification.externalcontracts;
import com.aws.jverify.*;
import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.*;

@JVerifyTest(javaVerified = 2, javaErrors = 0)
class NestedContractsTest {
    public static void foo(InterfaceOne.Nested f, InterfaceTwo.Nested f2, InterfaceTwo.Nested.ReNested f3) {
        f.setY(42);
        check(f.getY() == 42);
        f2.setY(42);
        check(f2.getY() == 43);
        f3.setV(42);
        check(f3.getV() == 42);
    }

    @Impure
    interface InterfaceOne {
        @Impure
        interface Nested {
            void setY(int x);
            int getY();
        }
    }

    @Impure
    interface InterfaceTwo {
        @Impure
        interface Nested {
            void setY(int x);
            int getY();
            @Impure
            interface ReNested {
                void setV(int x);
                int getV();
            }
        }
    }

    @Contract(InterfaceOne.Nested.class)
    static class InterfaceOneContract implements InterfaceOne.Nested {
        int value;
        public void setY(int x) {
            postcondition(this.value == x);
            throw new ContractException();
        }
        @Pure
        public int getY() {
            reads(this);
            postcondition((int i) -> i == value);
            throw new ContractException();
        }
    }

    @Contract(InterfaceTwo.Nested.class)
    static class InterfaceTwoNestedContract implements InterfaceTwo.Nested {
        int value;
        public void setY(int x) {
            postcondition(this.value == x);
            throw new ContractException();
        }
        @Pure
        public int getY() {
            reads(this);
            postcondition((int i) -> i == value+1);
            throw new ContractException();
        }
    }

    @Contract(InterfaceTwo.Nested.ReNested.class)
    class InterfaceTwoNestedReNested implements InterfaceTwo.Nested.ReNested {
        int value;
        public void setV(int x) {
            postcondition(this.value == x);
            throw new ContractException();
        }
        @Pure
        public int getV() {
            reads(this);
            postcondition((int i) -> i == value);
            throw new ContractException();
        }
    }
}
