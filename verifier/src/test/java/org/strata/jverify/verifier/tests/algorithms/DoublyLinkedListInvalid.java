package org.strata.jverify.verifier.tests.algorithms;

import org.strata.jverify.Erased;
import org.strata.jverify.Nullable;
import org.strata.jverify.Transparent;
import org.strata.jverify.Unbounded;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(expectedToFail = true, blockedBy = {Blocker.FRONTEND_OTHER, Blocker.STRATA_NULLABLE}, exitCode = 4)
class DoublyLinkedListInvalid {
    static class Node {
        int value;
        @Nullable Node prev;
        @Nullable Node next;
    }

    @Transparent
    @Erased
    static boolean linkedFrom(@Nullable Node prev, @Nullable Node n, @Unbounded int len) {
        reads(everything());
        decreases(len);
        if (n == null) {
            return len == 0;
        }
        return len > 0 && n.prev == prev && linkedFrom(n, n.next, len - 1);
    }

    @Transparent
    @Erased
    static int valueAt(@Nullable Node n, @Unbounded int k) {
        reads(everything());
        decreases(k);
        if (n == null) {
            return 0;
        }
        if (k == 0) {
            return n.value;
        }
        return valueAt(n.next, k - 1);
    }

    @Transparent
    @Erased
    static @Nullable Node nodeAt(@Nullable Node n, @Unbounded int k) {
        reads(everything());
        decreases(k);
        if (n == null || k == 0) {
            return n;
        }
        return nodeAt(n.next, k - 1);
    }

    static void insertAfter(Node head, @Unbounded int len, @Unbounded int pos, int v) {
        precondition(linkedFrom(null, head, len));
        precondition(0 <= pos && pos < len);
        modifies(everything());
        postcondition(linkedFrom(null, head, len + 1));
//                    ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ Error: postcondition could not be proved
        postcondition(valueAt(head, pos + 1) == v);
        postcondition(forall((int k) -> implies(0 <= k && k <= pos, valueAt(head, k) == old(valueAt(head, k)))));
        postcondition(forall((int k) -> implies(pos < k && k < len, valueAt(head, k + 1) == old(valueAt(head, k)))));

        Node at = head;
        @Unbounded int i = 0;
        while (i < pos) {
            invariant(0 <= i && i <= pos);
            invariant(at == nodeAt(head, i));
            at = at.next;
            i = i + 1;
        }
        Node fresh = new Node();
        fresh.value = v;
        fresh.prev = at;
        fresh.next = at.next;
        if (at.next != null) {
            at.next.prev = at;
        }
        at.next = fresh;
    }

    static void unlink(Node head, @Unbounded int len, Node target, @Unbounded int pos) {
        precondition(linkedFrom(null, head, len));
        precondition(0 < pos && pos < len && target == nodeAt(head, pos));
        modifies(everything());
        postcondition(linkedFrom(null, head, len - 1));
        postcondition(forall((int k) -> implies(0 <= k && k < pos, valueAt(head, k) == old(valueAt(head, k)))));
        postcondition(forall((int k) -> implies(pos <= k && k < len - 1, valueAt(head, k) == old(valueAt(head, k + 1)))));

        Node before = target.prev;
        @Nullable Node after = target.next;
        before.next = after;
        if (after != null) {
            after.prev = before;
        }
        target.prev = null;
        target.next = null;
    }
}
