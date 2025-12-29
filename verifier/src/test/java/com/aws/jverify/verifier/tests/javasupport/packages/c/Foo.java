package com.aws.jverify.verifier.tests.javasupport.packages.c;

import com.aws.jverify.Pure;

import static com.aws.jverify.JVerify.check;
import static com.aws.jverify.JVerify.precondition;

public class Foo {
    void fails() {
        check(false);
    }
}
