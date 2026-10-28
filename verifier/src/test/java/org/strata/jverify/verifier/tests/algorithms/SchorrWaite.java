package org.strata.jverify.verifier.tests.algorithms;

import org.strata.jverify.Erased;
import org.strata.jverify.Nullable;
import org.strata.jverify.Transparent;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(expectedToFail = true, blockedBy = {Blocker.FRONTEND_ARRAYS}, exitCode = 0)
class SchorrWaite {
    static class Node {
        boolean marked;
        boolean visitingRight;
        @Nullable Node left;
        @Nullable Node right;
    }

    @Transparent
    @Erased
    static boolean member(Node[] all, @Nullable Node n) {
        reads(all);
        return n == null || exists((int i) -> 0 <= i && i < all.length && all[i] == n);
    }

    @Transparent
    @Erased
    static boolean closedWorld(Node[] all) {
        reads(everything());
        return forall((int i) -> implies(0 <= i && i < all.length,
                all[i] != null && member(all, all[i].left) && member(all, all[i].right)));
    }

    @Transparent
    @Erased
    static boolean markClosed(Node[] all) {
        reads(everything());
        return forall((int i) -> implies(0 <= i && i < all.length && all[i].marked,
                (all[i].left == null || all[i].left.marked) && (all[i].right == null || all[i].right.marked)));
    }

    static void mark(Node[] all, @Nullable Node root) {
        precondition(closedWorld(all));
        precondition(member(all, root));
        precondition(forall((int i) -> implies(0 <= i && i < all.length, !all[i].marked)));
        modifies(everything());
        postcondition(root == null || root.marked);
        postcondition(markClosed(all));
        postcondition(forall((int i) -> implies(0 <= i && i < all.length,
                all[i].left == old(all[i].left) && all[i].right == old(all[i].right))));

        @Nullable Node t = root;
        @Nullable Node p = null;
        while (p != null || (t != null && !t.marked)) {
            modifies(everything());
            invariant(closedWorld(all));
            invariant(member(all, t) && member(all, p));

            if (t == null || t.marked) {
                if (p.visitingRight) {
                    Node q = t;
                    t = p;
                    p = p.right;
                    t.right = q;
                } else {
                    Node q = t;
                    t = p.right;
                    p.right = p.left;
                    p.left = q;
                    p.visitingRight = true;
                }
            } else {
                Node q = p;
                p = t;
                t = t.left;
                p.left = q;
                p.marked = true;
                p.visitingRight = false;
            }
        }
    }
}
