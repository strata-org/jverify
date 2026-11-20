package org.strata.jverify.verifier.tests.javasupport.statics;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * Static fields of a class with no static initializer: reads and writes inside
 * the declaring class. A static field holds an arbitrary value when a method
 * starts, so a method knows only what it wrote itself or what its precondition
 * says.
 */
@JVerifyTest(continueOnErrors = true, exitCode = 4, errorCount = 1)
class StaticFieldReadWrite {

    static int counter;
    static boolean enabled;

    static void write() {
        counter = 5;
        check(counter == 5);
    }

    static void arbitraryAtEntry() {
        check(counter == 0);
//      ^^^^^^^^^^^^^^^^^^^^ Error: assertion does not hold
    }

    static void fromPrecondition() {
        precondition(counter == 7);
        check(counter == 7);
    }

    static void increment() {
        precondition(counter < 100);
        postcondition(counter <= 100);
        counter++;
    }

    static void twoFields() {
        counter = 1;
        enabled = true;
        check(counter == 1 && enabled);
    }

    static void incrementUsedAsValue() {
        counter = 3;
        int before = counter++;
        check(before == 3);
        check(counter == 4);
    }

    void fromInstanceMethod() {
        counter = 9;
        check(counter == 9);
    }
}
