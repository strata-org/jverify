// ^ /builtin-contracts.java(73:36-73:50) Related location: this proposition could not be proved
package com.aws.jverify.verifier.tests;

import com.aws.jverify.testengine.JVerifyTest;

import java.util.List;

import static com.aws.jverify.JVerify.check;

@JVerifyTest(exitCode = 4, dafnyVerified = 3, dafnyErrors = 2, useBuiltinContracts = true)
public class ListTest {

    void verifying() {
        var s = List.of("one", "two", "three");
        check(s.size() == 3);
        check(s.get(1).equals("two"));
        check(s.contains("two"));

        var s2 = List.of();
        check(s2.isEmpty());
    }

    void checkFails() {
        var s = List.of("one", "two");
        check(s.size() ==  0);
//      ^^^^^^^^^^^^^^^^^^^^^ Error: assertion might not hold
    }

    void preconditionFails() {
        var s = List.of("one", "two");
        var x = s.get(2);
//              ^^^^^^^^ Error: function precondition could not be proved
    }
}
