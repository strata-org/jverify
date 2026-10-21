package org.strata.jverify.verifier.tests.algorithms;

import org.strata.jverify.Erased;
import org.strata.jverify.Nullable;
import org.strata.jverify.Pure;
import org.strata.jverify.Unbounded;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(expectedToFail = true, blockedBy = {Blocker.FRONTEND_OTHER, Blocker.STRATA_NULLABLE}, exitCode = 0)
class SinglyLinkedList {
    static class Node {
        int value;
        @Nullable Node next;
    }

    @Pure
    @Erased
    static boolean isList(@Nullable Node n, @Unbounded int len) {
        reads(everything());
        decreases(len);
        if (n == null) {
            return len == 0;
        }
        return len > 0 && isList(n.next, len - 1);
    }

    @Pure
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

    @Pure
    @Erased
    static @Unbounded int sumOf(@Nullable Node n, @Unbounded int len) {
        reads(everything());
        decreases(len);
        if (n == null || len <= 0) {
            return 0;
        }
        return n.value + sumOf(n.next, len - 1);
    }

    static @Unbounded int sum(@Nullable Node head, @Unbounded int len) {
        precondition(isList(head, len));
        postcondition((int res) -> res == sumOf(head, len));

        @Unbounded int s = 0;
        @Nullable Node cur = head;
        @Unbounded int seen = 0;
        while (cur != null) {
            invariant(0 <= seen && seen <= len);
            invariant(isList(cur, len - seen));
            invariant(s + sumOf(cur, len - seen) == sumOf(head, len));

            s = s + cur.value;
            cur = cur.next;
            seen = seen + 1;
        }
        return s;
    }

    static @Nullable Node reverse(@Nullable Node head, @Unbounded int len) {
        precondition(isList(head, len));
        modifies(everything());
        postcondition((Node res) -> isList(res, len));
        postcondition((Node res) -> forall((int k) -> implies(0 <= k && k < len,
                valueAt(res, k) == old(valueAt(head, len - 1 - k)))));

        @Nullable Node prev = null;
        @Nullable Node cur = head;
        @Unbounded int done = 0;
        while (cur != null) {
            modifies(everything());
            invariant(0 <= done && done <= len);
            invariant(isList(prev, done));
            invariant(isList(cur, len - done));

            @Nullable Node next = cur.next;
            cur.next = prev;
            prev = cur;
            cur = next;
            done = done + 1;
        }
        return prev;
    }
}
