package org.strata.jverify.verifier.tests.algorithms;

import org.strata.jverify.Erased;
import org.strata.jverify.Nullable;
import org.strata.jverify.Pure;
import org.strata.jverify.Unbounded;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(expectedToFail = true, blockedBy = {Blocker.STRATA_NULLABLE, Blocker.STRATA_FRESH}, exitCode = 0)
class DeepClone {
    static class Node {
        int value;
        @Nullable Node left;
        @Nullable Node right;
    }

    @Pure
    @Erased
    static boolean isTree(@Nullable Node n, @Unbounded int height) {
        reads(everything());
        decreases(height);
        if (n == null) {
            return true;
        }
        return height > 0 && isTree(n.left, height - 1) && isTree(n.right, height - 1);
    }

    @Pure
    @Erased
    static boolean isomorphic(@Nullable Node a, @Nullable Node b, @Unbounded int height) {
        reads(everything());
        decreases(height);
        if (a == null || b == null) {
            return a == null && b == null;
        }
        return height > 0 && a.value == b.value
                && isomorphic(a.left, b.left, height - 1) && isomorphic(a.right, b.right, height - 1);
    }

    @Pure
    @Erased
    static boolean disjoint(@Nullable Node a, @Nullable Node b, @Unbounded int ha, @Unbounded int hb) {
        reads(everything());
        decreases(ha + hb);
        if (a == null || b == null) {
            return true;
        }
        return ha > 0 && hb > 0 && a != b
                && disjoint(a.left, b, ha - 1, hb) && disjoint(a.right, b, ha - 1, hb)
                && disjoint(a, b.left, ha, hb - 1) && disjoint(a, b.right, ha, hb - 1);
    }

    static @Nullable Node deepClone(@Nullable Node n, @Unbounded int height) {
        precondition(isTree(n, height));
        decreases(height);
        postcondition((Node res) -> isTree(res, height));
        postcondition((Node res) -> isomorphic(res, n, height));
        postcondition((Node res) -> disjoint(res, n, height, height));
        postcondition((Node res) -> res == null || fresh(res));

        if (n == null) {
            return null;
        }
        Node copy = new Node();
        copy.value = n.value;
        copy.left = deepClone(n.left, height - 1);
        copy.right = deepClone(n.right, height - 1);
        return copy;
    }
}
