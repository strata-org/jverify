// TEST: exitCode=4 dafnyVerified=5 dafnyErrors=3
package com.aws.jverify.verifier.tests;

import com.aws.jverify.*;

import static com.aws.jverify.JVerify.*;

interface I {
    int f(int x);
    int m();
}

@Contract(I.class)
class IContract implements I {

    @Pure
    public int f(int x) {
        precondition(x > 2);
        reads(this);
        throw new ContractException();
    }

    public int m() {
        modifies(this);
        postcondition((Integer r) -> r > 2);
        throw new ContractException();
    }
}

class Container {
    int x;
}

class CInvalidContract implements I {
    Container c;

    CInvalidContract() {
        c = new Container();
    }
    
    @Override
    public int f(int x) {
//             ^ Error: contract not inherited
        return c.x;
    }

    @Override
    public int m() {
//             ^ Error: contract not inherited
        return 0;
    }
}

class CValid implements I {
    @Nullable Object obj;

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
        postcondition((Integer r) -> r > 2);
        obj = new Object();
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
        postcondition((Integer r) -> r > 2);
        c.x = 3;
//      ^^^ Error: assignment might update an object not in the enclosing context's modifies clause
        return 0;
    }
}