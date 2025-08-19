// ^ /builtin-contracts.java(539:22-539:27) Related location: this proposition could not be proved
package com.aws.jverify.verifier.tests;

import com.aws.jverify.testengine.JVerifyTest;

import java.util.Optional;

import static com.aws.jverify.JVerify.check;

@JVerifyTest(exitCode = 4, dafnyVerified = 2, dafnyErrors = 3, useBuiltinContracts = true)
public class Optionals {

    void testEmpty() {
        var opt = Optional.empty();
        check(opt.isEmpty());
        check(!opt.isPresent());
        var res = opt.get();
//                ^^^^^^^^^ Error: function precondition could not be proved
        var res1 = opt.orElse("Hello");
        check(res1.equals("Hello"));
    }

    void testOf() {
        var opt = Optional.of("Hello");
        check(opt.get().equals("Hello"));
        check(opt.isEmpty());
//      ^^^^^^^^^^^^^^^^^^^^ Error: assertion might not hold
        check(opt.isPresent());
        var res = opt.orElse("World");
        check(res.equals("Hello"));
    }

    void testOfNullable() {
        var opt = Optional.ofNullable("5");
        check(opt.isPresent());
        check(opt.get().equals("7"));
//            ^^^^^^^^^^^^^^^^^^^^^ Error: assertion might not hold
        check(opt.get().equals("5"));
        check(opt.get().equals(opt.get()));
    }
}




