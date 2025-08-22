package com.aws.jverify.verifier.tests.javasupport.interfaces;

import com.aws.jverify.*;
import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.*;

@JVerifyTest(exitCode = 4, dafnyVerified = 23, dafnyErrors = 5)
class InterfacesVerification {
    public void root(I i) {
        var a = i.f(1);
//              ^^^^^^ Error: function precondition could not be proved
        var b = i.m();
        check(b > 2);
    }

    // Ensure various types extend Object in the encoding
    public void extendsObject(I i, INotModifiable inm) {
        Object x = i;
        Object y = inm;
    }
}

@Modifiable
interface I {
    int f(int x);
    int m();

    @Contract
    class IContract implements I {

        @Pure
        public int f(int x) {
            precondition(x > 2);
//                       ^^^^^ Related location: this proposition could not be proved
            reads(this);
            throw new ContractException();
        }

        public int m() {
            modifies(this);
            postcondition((int r) -> r > 2);
            throw new ContractException();
        }
    }
}

class User {

}

class Container {
    int x;
}

class CInvalidContract implements I {
    Container c;

    CInvalidContract() {
        c = new Container();
    }
    
    @Pure
    @Override
    public int f(int x) {
//             ^ Error: function's (possibly automatically generated) decreases clause must be below or equal to that in the trait
        return 0;
    }

    @Override
    public int m() {
//             ^ Error: the method must provide an equal or more detailed postcondition than in its parent trait
        return 0;
    }
}

class Anything {}

class CValid implements I {
    @Nullable Anything obj;

    //@InheritContract
    @Pure
    @Override
    public int f(int x) {
        precondition(x > 2);
        reads(this);
        return obj == null ? 3 : 4;
    }

    //@InheritContract
    @Override
    public int m() {
        modifies(this);
        postcondition((int r) -> r > 2);
        obj = new Anything();
        return 3;
    }
}

class CInvalidImplementation implements I {
    Container c;

    CInvalidImplementation() {
        c = new Container();
    }

    //@InheritContract
    @Pure
    @Override
    public int f(int x) {
        precondition(x > 2);
        reads(this);
        return c.x;
//             ^^^ Error: insufficient reads clause to read field; Consider adding 'reads c' or 'reads c`x' in the enclosing function specification for resolution
    }

    //@InheritContract
    @Override
    public int m() {
        modifies(this);
        postcondition((int r) -> r > 2);
        c.x = 3;
//      ^^^ Error: assignment might update an object not in the enclosing context's modifies clause
        return 3;
    }
}

interface INotModifiable {}
