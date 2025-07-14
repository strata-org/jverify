package com.aws.jverify.verifier.tests;

import com.aws.jverify.Contract;
import com.aws.jverify.ContractException;
import com.aws.jverify.Pure;
import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.*;

@JVerifyTest(exitCode = 0, dafnyVerified = 16, dafnyErrors = 0)
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

    public abstract int computeX(int input);
    
    public int virtualMethod() {
        postcondition((int r) -> r >= 10);
        return 10;
    }

    @Contract
    class BaseContract extends Base {

        public BaseContract(int input) {
            super(input);
        }

        @Pure
        public int computeX(int input) {
            throw new ContractException();
        }
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