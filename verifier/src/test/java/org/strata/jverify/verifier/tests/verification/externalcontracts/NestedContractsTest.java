package org.strata.jverify.verifier.tests.verification.externalcontracts;
import org.strata.jverify.*;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * A setter followed by a getter on the same receiver, three times over, where the
 * receiver is an interface-typed PARAMETER: its runtime tag is unknown, so the
 * caller reasons only from the external contract through the generated tag
 * dispatcher. Each getter's contract relates its result to the field the setter
 * wrote ({@code i == value}, and deliberately {@code i == value+1} for
 * {@code InterfaceTwo.Nested}, which is why that check expects 43).
 *
 * <p>This was blocked by the dispatch refusal, and then xfailed STRATA_OTHER
 * because the setter's effect did not reach the getter's declared postcondition
 * across the dispatched call -- suspected {@code LiftInstanceProcedures}
 * {@code dispatcherPosts} / frame handling. That has since been fixed on the Strata
 * side, so all three checks now verify and the fixture is a normal test again. It is
 * worth keeping exactly as-is: it pins modular reasoning through the dispatcher for
 * an unknown receiver tag.
 */
@JVerifyTest(methodsVerified = 2, errorCount = 0)
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
