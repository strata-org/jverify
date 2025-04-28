// TEST: exitCode=0 dafnyVerified=0 dafnyErrors=0
package com.aws.jverify.verifier.tests;

import com.aws.jverify.Contract;
import com.aws.jverify.ContractException;
import com.aws.jverify.Pure;

import java.lang.module.ModuleDescriptor;
import java.lang.reflect.Array;

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

class CValid implements I {
    Object object;
    
    @Override
    public int f(int x) {
        return object == null ? 3 : 4;
    }

    @Override
    public int m() {
        object = new Object();
        return 3;
    }
}

class CInvalid implements I {
    int[] array;
    
    @Override
    public int f(int x) {
        return array.length;
    }

    @Override
    public int m() {
        array[0] = 3;
        return 0;
    }
}