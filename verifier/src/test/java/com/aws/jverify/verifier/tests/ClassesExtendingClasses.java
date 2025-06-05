package com.aws.jverify.verifier.tests;

import com.aws.jverify.Contract;
import com.aws.jverify.ContractException;
import com.aws.jverify.Pure;
import com.aws.jverify.Verify;
import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.*;

@JVerifyTest(exitCode = 0, dafnyVerified = 11, dafnyErrors = 0)
public class ClassesExtendingClasses {
    public void root() {
        Extendee extender = new Extender(4);
        check(extender.getX() == 3);
    }
}

class Extender extends Extendee {
    int y;

    public Extender(int input) {
        super(input + 2);
        precondition(input < 10);
        postcondition(this.x == 3);
        y = 2;
    }

    @Pure
    @Override
    public int computeX(int input) {
        return 3;
    }
}

@Contract(Extendee.class)
class ExtendeeContract extends Extendee {

    public ExtendeeContract(int input) {
        super(input);
    }

    @Pure
    @Override
    public int computeX(int input) {
        throw new ContractException();
    }
}

abstract class Extendee {
    int x;
    
    public Extendee(int input) {
        postcondition(this.x == computeX(input));
        this.x = computeX(input);
    }

    @Pure
    public int getX() {
        reads(this);
        return x;
    }

    public abstract int computeX(int input);
    
    // TODO forbid overriding @Pure members
//    @Pure
//    public int computeX(int input) {
//        return input * 2;
//    }
}
