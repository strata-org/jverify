package org.strata.jverify.verifier.tests.algorithms;

import org.strata.jverify.Erased;
import org.strata.jverify.Nullable;
import org.strata.jverify.Transparent;
import org.strata.jverify.Unbounded;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(expectedToFail = true, blockedBy = {Blocker.STRATA_NULLABLE}, exitCode = 4)
class BinarySearchTreeInvalid {
    static class Node {
        int key;
        @Nullable Node left;
        @Nullable Node right;
    }

    @Transparent
    @Erased
    static boolean isBst(@Nullable Node n, @Unbounded int lo, @Unbounded int hi, @Unbounded int height) {
        reads(everything());
        decreases(height);
        if (n == null) {
            return true;
        }
        return height > 0 && lo < n.key && n.key < hi
                && isBst(n.left, lo, n.key, height - 1)
                && isBst(n.right, n.key, hi, height - 1);
    }

    @Transparent
    @Erased
    static boolean inTree(@Nullable Node n, int key, @Unbounded int height) {
        reads(everything());
        decreases(height);
        if (n == null || height <= 0) {
            return false;
        }
        return n.key == key || inTree(n.left, key, height - 1) || inTree(n.right, key, height - 1);
    }

    static boolean contains(@Nullable Node root, int key, @Unbounded int height) {
        precondition(isBst(root, -2147483648, 2147483647, height));
        precondition(-2147483648 < key && key < 2147483647);
        precondition(height >= 0);
        postcondition((boolean res) -> res == inTree(root, key, height));

        @Nullable Node cur = root;
        @Unbounded int h = height;
        while (cur != null) {
            invariant(0 <= h && h <= height);
            invariant(implies(inTree(root, key, height), inTree(cur, key, h)));
//                    ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ Error: assertion could not be proved
            invariant(implies(inTree(cur, key, h), inTree(root, key, height)));

            if (key == cur.key) {
                return true;
            }
            cur = key < cur.key ? cur.right : cur.left;
            h = h - 1;
        }
        return false;
    }

    static Node insert(@Nullable Node root, int key, @Unbounded int height) {
        precondition(isBst(root, -2147483648, 2147483647, height));
        precondition(-2147483648 < key && key < 2147483647);
        modifies(everything());
        decreases(height);
        postcondition((Node res) -> isBst(res, -2147483648, 2147483647, height + 1));
        postcondition((Node res) -> inTree(res, key, height + 1));

        if (root == null) {
            Node n = new Node();
            n.key = key;
            return n;
        }
        if (key < root.key) {
            root.left = insert(root.left, key, height - 1);
        } else if (key > root.key) {
            root.right = insert(root.right, key, height - 1);
        }
        return root;
    }
}
