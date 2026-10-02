package org.strata.jverify.verifier.tests.javasupport.classes;

import org.strata.jverify.EmptyContract;
import org.strata.jverify.Pure;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * {@code root} used to be refused wholesale ("polymorphic dispatch … not yet
 * supported"), so its assertions were never attempted. It is translated now, and the
 * two assertions this fixture exists for -- {@code virtualMethod} through both the
 * derived and the base static type -- pass. What still fails is
 * {@code check(extender.getX() == 3)}.
 *
 * <p>The cause is that CONSTRUCTORS are not translated. Everything the caller would
 * need to know about {@code extender.x} comes from {@code new Extender(4)}, whose
 * postcondition is {@code this.x == 3}; but a user constructor emits no Laurel at all,
 * and the {@code new} site discards its arguments and yields a bare allocation, so the
 * object's fields are completely unconstrained. No contract on {@code getX} can bridge
 * that -- which is exactly why adding {@code postcondition((int r) -> r == this.x)} to
 * {@code Base.getX} and {@code postcondition((int r) -> r == 3)} to
 * {@code Extender.computeX} each verified on their own yet left the call site
 * unprovable. Those additions were reverted for that reason.
 *
 * <p>This was previously xfailed STRATA_OTHER on the theory that it shared one
 * Strata-side frame bug with {@code NestedContractsTest} -- a dispatchable procedure's
 * frame not reaching the caller through the generated tag dispatcher. That theory is
 * now disproved: the Strata fix landed and {@code NestedContractsTest} verifies, while
 * this fixture does not. The two differ in where the fact has to come from -- there, an
 * external contract on an interface-typed parameter; here, a constructor.
 */
@JVerifyTest(expectedToFail = true,
             blockedBy = {Blocker.FRONTEND_FIELDS, Blocker.STRATA_ABSTRACT_PURE_FUNCTIONS},
             methodsVerified = 8, errorCount = 0)
public class ClassesExtendingClassesVerification {
    public void root() {
        Extender extender = new Extender(4);
        check(extender.getX() == 3);
        var re = extender.virtualMethod();
        check(re >= 20);
        Base base = extender;
        var rb = base.virtualMethod();
        check(rb >= 10);
    }
}

class Extender extends Base {
    int z;

    public Extender(int input) {
        super(input + 2);
        precondition(input < 10);
        postcondition(this.x == 3);
        z = 2;
    }

    @Pure
    @Override
    public int computeX(int input) {
        return 3;
    }

    @Override
    public int virtualMethod() {
        postcondition((int r) -> r >= 20);
        return 20;
    }
}


abstract class Base {
    int x;
    final int y;
    
    public Base(int input) {
        postcondition(this.x == computeX(input));
        this.x = computeX(input);
        this.y = 3;
    }

    @Pure
    public int getX() {
        reads(this);
        return x;
    }

    @Pure
    @EmptyContract
    public abstract int computeX(int input);
    
    public int virtualMethod() {
        postcondition((int r) -> r >= 10);
        return 10;
    }
}

interface IExtendee {
    default int virtualMethod() {
        postcondition((int r) -> r >= 10);
        return 10;
    }
}

interface IExtender extends IExtendee {
    @Override
    default int virtualMethod() {
        postcondition((int r) -> r >= 20);
        return 20;
    }
}
