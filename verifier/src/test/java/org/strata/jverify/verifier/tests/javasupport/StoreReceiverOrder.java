package org.strata.jverify.verifier.tests.javasupport;

import static org.strata.jverify.JVerify.*;

import org.strata.jverify.testengine.JVerifyTest;

@JVerifyTest(exitCode = 4, methodsVerified = 4, errorCount = 1)
class StoreReceiverOrder {

    static class Node {
        int v;
        Node next;
    }

    static int redirect(Node h, Node other) {
        modifies(h);
        postcondition((int r) -> h.next == other && r == 7);
        h.next = other;
        return 7;
    }

    static void storesIntoTheOldReceiver(Node h, Node a, Node b) {
        precondition(h != a && h != b && a != b && h.next == a);
        modifies(h);
        modifies(a);
        modifies(b);
        a.v = 0;
        b.v = 0;
        h.next.v = redirect(h, b);
        check(a.v == 7);
        check(b.v == 0);
    }

    static void storesIntoTheNewReceiverWrongly(Node h, Node a, Node b) {
        precondition(h != a && h != b && a != b && h.next == a);
        modifies(h);
        modifies(a);
        modifies(b);
        a.v = 0;
        b.v = 0;
        h.next.v = redirect(h, b);
        check(b.v == 7);
//      ^^^^^^^^^^^^^^^^ Error: assertion could not be proved
    }
}
