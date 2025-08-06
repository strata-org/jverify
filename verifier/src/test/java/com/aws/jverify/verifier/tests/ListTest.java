package com.aws.jverify.verifier.tests;

import com.aws.jverify.Contract;
import com.aws.jverify.ContractException;
import com.aws.jverify.JVerify;
import com.aws.jverify.Modifiable;
import com.aws.jverify.Pure;
import com.aws.jverify.testengine.JVerifyTest;

import java.util.List;
import java.util.Objects;

import static com.aws.jverify.JVerify.check;
import static com.aws.jverify.JVerify.postcondition;
import static com.aws.jverify.JVerify.precondition;
import static com.aws.jverify.JVerify.reads;

@JVerifyTest(dafnyVerified = 8, dafnyErrors = 0)
public class ListTest {

    void verifying() {
        var s = List.of("one", "two", "three");
        check(s.size() == 3);
        check(s.get(1).equals("two"));
        check(s.contains("two"));

        var s2 = List.of();
        check(s2.isEmpty());
    }

    void notVerifying() {
        var s = List.of("one", "two");
        check(s.size() ==  0);
        var x = s.get(2);
    }
}
