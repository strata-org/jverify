package org.strata.jverify.verifier.tests.javasupport.statics;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * A static field read is saved at the instruction that reads it, like every
 * other operand, so the value an expression uses is the one the field had
 * when Java read it: {@code setX() == x} calls {@code setX()} first, which
 * sets {@code x}, and then reads it.
 */
@JVerifyTest(interpret = true, entry = "org.strata.jverify.verifier.tests.javasupport.statics.StaticReadOrder#run")
class StaticReadOrder {

    // boolean: the Laurel pipeline lets no entry procedure reach a global of a
    // constrained type such as int, not even through a call.
    static boolean x;

    static boolean setX() {
        x = true;
        return false;
    }

    static boolean inOrder() {
        x = false;
        boolean r = setX() == x;    // false == true
        return !r;
    }

    static void run() {
        check(inOrder());
    }
}
