package org.strata.jverify.verifier.tests.javasupport.classes;

import org.strata.jverify.EmptyContract;
import org.strata.jverify.Pure;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(methodsVerified = 11, errorCount = 0)
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
