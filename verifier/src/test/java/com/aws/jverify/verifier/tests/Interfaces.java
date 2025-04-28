package com.aws.jverify.verifier.tests;

import com.aws.jverify.ContractException;
import com.aws.jverify.Pure;

import java.lang.module.ModuleDescriptor;

import static com.aws.jverify.JVerify.*;

interface I {
    @Pure
    int f(int x);

    @Pure
    default int g(int x) {
        precondition(x > 2);
        reads(this);
        throw new ContractException();
    }
    
    void m();

    default int m2() {
        modifies(this);
        postcondition((Integer r) -> r > 2);
        throw new ContractException();
    }   
}

class C implements I {

    @Override
    public int f(int x) {
        return 0;
    }

    @Override
    public void m() {

    }

    @Override
    public int g(int x) {
        return 0;
    }

    @Override
    public int m2() {
        return 0;
    }
}