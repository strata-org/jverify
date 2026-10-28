package org.strata.jverify.verifier.tests.algorithms;

import org.strata.jverify.Erased;
import org.strata.jverify.Nullable;
import org.strata.jverify.Transparent;
import org.strata.jverify.Unbounded;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(expectedToFail = true, blockedBy = {Blocker.FRONTEND_ARRAYS, Blocker.FRONTEND_OTHER, Blocker.STRATA_NULLABLE}, exitCode = 0)
class BinaryTrie {
    static class Node {
        final @Nullable Node[] child = new @Nullable Node[2];
        boolean terminal;
    }

    @Transparent
    @Erased
    static boolean isWord(int[] word) {
        reads(word);
        return forall((int k) -> implies(0 <= k && k < word.length, word[k] == 0 || word[k] == 1));
    }

    @Transparent
    @Erased
    static boolean storedFrom(@Nullable Node n, int[] word, @Unbounded int depth) {
        reads(everything());
        decreases(word.length - depth);
        if (n == null || depth < 0 || depth > word.length) {
            return false;
        }
        if (depth == word.length) {
            return n.terminal;
        }
        return storedFrom(n.child[word[depth]], word, depth + 1);
    }

    static boolean contains(Node root, int[] word) {
        precondition(isWord(word));
        postcondition((boolean res) -> res == storedFrom(root, word, 0));

        @Nullable Node cur = root;
        int d = 0;
        while (cur != null && d < word.length) {
            invariant(0 <= d && d <= word.length);
            invariant(storedFrom(root, word, 0) == storedFrom(cur, word, d));

            cur = cur.child[word[d]];
            d = d + 1;
        }
        return cur != null && cur.terminal;
    }

    static void insert(Node root, int[] word) {
        precondition(isWord(word));
        modifies(everything());
        postcondition(storedFrom(root, word, 0));

        Node cur = root;
        int d = 0;
        while (d < word.length) {
            modifies(everything());
            invariant(0 <= d && d <= word.length);

            @Nullable Node next = cur.child[word[d]];
            if (next == null) {
                next = new Node();
                cur.child[word[d]] = next;
            }
            cur = next;
            d = d + 1;
        }
        cur.terminal = true;
    }
}
