// ^ /aws/jverify/builtin/OptionalContract.java(57:22-57:27) Related location: this proposition could not be proved
package org.strata.jverify.verifier.tests.jcl;

import org.strata.jverify.testengine.JVerifyTest;

import java.util.Optional;

import static org.strata.jverify.JVerify.check;

@JVerifyTest(skip = "Strata: not yet supported", exitCode = 4, methodsVerified = 1, errorCount = 2, useBuiltinContracts = true)
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
//      ^^^^^^^^^^^^^^^^^^^^ Error: assertion does not hold
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
            ^^^^^^^^^^^^^^^^^^^^^ Error: assertion does not hold
        check(opt.get().equals("5"));
        check(opt.get().equals(opt.get()));
    }
 */

}




