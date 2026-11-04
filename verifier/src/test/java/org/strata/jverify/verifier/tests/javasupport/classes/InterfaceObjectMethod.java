package org.strata.jverify.verifier.tests.javasupport.classes;

import org.strata.jverify.Contract;
import org.strata.jverify.ContractException;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.check;
import static org.strata.jverify.JVerify.postcondition;

/**
 * An interface inherits no method of {@code Object}, so {@code s.hashCode()} binds the {@code Keyed.hashCode()} that
 * {@code Sub} inherits, and takes its contract, which {@code K}'s override satisfies.
 */
@JVerifyTest(methodsVerified = 4)
class InterfaceObjectMethod {
    interface Keyed {
        int hashCode();
    }

    @Contract(Keyed.class)
    static class KeyedContract implements Keyed {
        public int hashCode() {
            postcondition((int r) -> r >= 0);
            throw new ContractException();
        }
    }

    interface Sub extends Keyed {
    }

    static class K implements Sub {
        public int hashCode() {
            postcondition((int r) -> r == 1);
            return 1;
        }
    }

    static void use(Sub s) {
        check(s.hashCode() >= 0);
    }
}
