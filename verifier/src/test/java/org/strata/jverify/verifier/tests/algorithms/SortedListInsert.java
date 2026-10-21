package org.strata.jverify.verifier.tests.algorithms;

import org.strata.jverify.Erased;
import org.strata.jverify.Nullable;
import org.strata.jverify.Pure;
import org.strata.jverify.Unbounded;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(expectedToFail = true, blockedBy = {Blocker.FRONTEND_OTHER, Blocker.STRATA_NULLABLE}, exitCode = 0)
class SortedListInsert {
    static class Node {
        int value;
        @Nullable Node next;
    }

    @Pure
    @Erased
    static boolean sortedList(@Nullable Node n, @Unbounded int len) {
        reads(everything());
        decreases(len);
        if (n == null) {
            return len == 0;
        }
        if (len <= 0) {
            return false;
        }
        if (n.next != null && n.value > n.next.value) {
            return false;
        }
        return sortedList(n.next, len - 1);
    }

    @Pure
    @Erased
    static @Unbounded int occurrences(@Nullable Node n, int v, @Unbounded int len) {
        reads(everything());
        decreases(len);
        if (n == null || len <= 0) {
            return 0;
        }
        return (n.value == v ? 1 : 0) + occurrences(n.next, v, len - 1);
    }

    static Node insert(@Nullable Node head, int v, @Unbounded int len) {
        precondition(sortedList(head, len));
        modifies(everything());
        postcondition((Node res) -> sortedList(res, len + 1));
        postcondition((Node res) -> forall((int w) ->
                occurrences(res, w, len + 1) == old(occurrences(head, w, len)) + (w == v ? 1 : 0)));

        Node fresh = new Node();
        fresh.value = v;
        if (head == null || v <= head.value) {
            fresh.next = head;
            return fresh;
        }
        Node cur = head;
        while (cur.next != null && cur.next.value < v) {
            modifies(everything());
            invariant(cur.value < v);
            cur = cur.next;
        }
        fresh.next = cur.next;
        cur.next = fresh;
        return head;
    }
}
