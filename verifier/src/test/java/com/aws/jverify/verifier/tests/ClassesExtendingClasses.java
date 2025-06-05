package com.aws.jverify.verifier.tests;

import com.aws.jverify.Contract;
import com.aws.jverify.ContractException;
import com.aws.jverify.Pure;
import com.aws.jverify.Verify;
import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.check;
import static com.aws.jverify.JVerify.postcondition;

@JVerifyTest(exitCode = 4, dafnyVerified = 0, dafnyErrors = 1)
public class ClassesExtendingClasses {
    public void root() {
        Extendee extender = new Extender(4);
        check(extender.getX() == 3);
    }
}

@Contract(Extendee.class)
class ExtendeeContract extends Extendee {

    @Pure
    @Override
    public int computeX(int input) {
        throw new ContractException();
    }
}

abstract class Extendee {
    int x;
    
//    public Extendee(int input) {
//        postcondition(this.x == computeX(input));
//        this.x = computeX(input);
//    }

    @Pure
    public int getX() {
        return x;
    }

    public abstract int computeX(int input);
    
    // TODO support: overriding existing members
//    @Pure
//    public int computeX(int input) {
//        return input * 2;
//    }
}

class Extender extends Extendee {
    int y;

    public Extender(int input) {
        //super(input + 2);
        postcondition(this.x == 3);
        y = 2;
    }

    @Pure
    @Override
    public int computeX(int input) {
        return 3;
    }
}
