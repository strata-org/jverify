package org.strata.jverify.verifier.tests.algorithms;

import org.strata.jverify.Erased;
import org.strata.jverify.Nullable;
import org.strata.jverify.Transparent;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(expectedToFail = true, blockedBy = {Blocker.FRONTEND_ARRAYS, Blocker.FRONTEND_OTHER, Blocker.STRATA_NULLABLE}, exitCode = 4)
class CompositeSizeInvalid {
    static class Node {
        int size;
        @Nullable Node parent;
        @Nullable Node left;
        @Nullable Node right;
    }

    @Transparent
    @Erased
    static int sizeOf(@Nullable Node n) {
        reads(everything());
        return n == null ? 0 : n.size;
    }

    @Transparent
    @Erased
    static boolean member(Node[] all, @Nullable Node n) {
        reads(all);
        return n == null || exists((int i) -> 0 <= i && i < all.length && all[i] == n);
    }

    @Transparent
    @Erased
    static boolean consistent(Node[] all) {
        reads(everything());
        return forall((int i) -> implies(0 <= i && i < all.length,
                all[i] != null
                && member(all, all[i].parent) && member(all, all[i].left) && member(all, all[i].right)
                && all[i].size == 1 + sizeOf(all[i].left) + sizeOf(all[i].right)
                && (all[i].left == null || all[i].left.parent == all[i])
                && (all[i].right == null || all[i].right.parent == all[i])
                && (all[i].parent == null || all[i].parent.left == all[i] || all[i].parent.right == all[i])));
    }

    @Transparent
    @Erased
    static boolean sizesBounded(Node[] all) {
        reads(everything());
        return all.length < 1000000 && forall((int i) -> implies(0 <= i && i < all.length,
                1 <= all[i].size && all[i].size <= all.length));
    }

    static void addLeft(Node[] all, Node p, Node child) {
        precondition(consistent(all));
        precondition(sizesBounded(all));
        precondition(member(all, p) && p.left == null);
        precondition(member(all, child) && child.parent == null && child.left == null && child.right == null);
        precondition(child != p);
        modifies(everything());
        postcondition(consistent(all));
//                    ^^^^^^^^^^^^^^^ Error: postcondition could not be proved
        postcondition(p.left == child);

        p.left = child;
        child.parent = p;
        @Nullable Node cur = p;
        while (cur != null && cur.parent != null) {
            modifies(everything());
            invariant(member(all, cur));
            cur.size = cur.size + 1;
            cur = cur.parent;
        }
    }
}
