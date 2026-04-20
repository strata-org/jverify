// ^ /aws/jverify/builtin/ListContract.java(48:36-48:50) Related location: this proposition could not be proved
package org.strata.jverify.verifier.tests.jcl;

import org.strata.jverify.testengine.JVerifyTest;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.strata.jverify.JVerify.check;

@JVerifyTest(skip = "Strata: not yet supported", exitCode = 4, methodsVerified = 4, errorCount = 4, useBuiltinContracts = true)
public class CollectionsTest {

    void listVerifying() {
        var s = List.of("one", "two", "three");
        check(s.size() == 3);
        check(s.get(1).equals("two"));
        check(s.contains("two"));

        var s2 = List.of();
        check(s2.isEmpty());
    }

    void listCheckFails() {
        var s = List.of("one", "two");
        check(s.size() ==  0);
//      ^^^^^^^^^^^^^^^^^^^^^ Error: assertion does not hold
    }

    void listPreconditionFails() {
        var s = List.of("one", "two");
        var x = s.get(2);
//              ^^^^^^^^ Error: function precondition could not be proved
    }

    void setVerifying() {
        var s = Set.of("one", "two", "three");
        check(s.size() == 3);
        check(s.contains("two"));
        // TODO: Need a stronger contract on equals to prove this
//        check(!s.contains("four"));

        var s2 = Set.of();
        check(s2.isEmpty());
    }

    void setCheckFails() {
        var s = Set.of("one", "two");
        check(s.size() ==  0);
//      ^^^^^^^^^^^^^^^^^^^^^ Error: assertion does not hold
    }

    void mapVerifying() {
        var s = Map.of("one", "two", "three", "four");
        check(s.size() == 2);
        check(s.containsKey("three"));

        var entrySet = s.entrySet();
        check(entrySet.size() == 2);

        var s2 = Map.of();
        check(s2.isEmpty());
    }

    void mapCheckFails() {
        var s = Map.of("one", "two");
        check(s.size() ==  0);
//      ^^^^^^^^^^^^^^^^^^^^^ Error: assertion does not hold
    }
}
