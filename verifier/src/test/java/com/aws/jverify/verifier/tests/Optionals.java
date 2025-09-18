// ^ /builtin-contracts.java(550:22-550:27) Related location: this proposition could not be proved
package com.aws.jverify.verifier.tests;

import com.aws.jverify.testengine.JVerifyTest;

import java.util.Optional;

import static com.aws.jverify.JVerify.check;

@JVerifyTest(exitCode = 4, dafnyVerified = 2, dafnyErrors = 2, useBuiltinContracts = true)
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
//      ^^^^^^^^^^^^^^^^^^^^ Error: assertion could not be proved
        check(opt.isPresent());
        var res = opt.orElse("World");
        check(res.equals("Hello"));
    }

    // will re-enable once we can handle null checks on generic classes
    /*
    void testOfNullable() {
        var opt = Optional.ofNullable("5");
        check(opt.isPresent());
        check(opt.get().equals("7"));
            ^^^^^^^^^^^^^^^^^^^^^ Error: assertion could not be proved
        check(opt.get().equals("5"));
        check(opt.get().equals(opt.get()));
    }
 */

}




