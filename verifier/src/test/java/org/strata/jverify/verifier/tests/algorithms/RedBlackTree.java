package org.strata.jverify.verifier.tests.algorithms;

import org.strata.jverify.Erased;
import org.strata.jverify.Nullable;
import org.strata.jverify.Transparent;
import org.strata.jverify.Unbounded;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(expectedToFail = true, blockedBy = {Blocker.STRATA_NULLABLE, Blocker.FRONTEND_OTHER}, exitCode = 0)
class RedBlackTree {
    static class Node {
        int key;
        boolean red;
        int size;
        @Nullable Node left;
        @Nullable Node right;
    }

    @Transparent
    static int size(@Nullable Node n) {
        reads(everything());
        return n == null ? 0 : n.size;
    }

    @Transparent
    @Erased
    static boolean isRed(@Nullable Node n) {
        reads(everything());
        return n != null && n.red;
    }

    @Transparent
    @Erased
    static boolean orderedN(@Nullable Node n, @Unbounded int lo, @Unbounded int hi) {
        reads(everything());
        decreases(size(n));
        if (n == null) {
            return true;
        }
        return size(n.left) >= 0 && size(n.right) >= 0 && n.size == 1 + size(n.left) + size(n.right)
                && lo < n.key && n.key < hi
                && orderedN(n.left, lo, n.key) && orderedN(n.right, n.key, hi);
    }

    @Transparent
    @Erased
    static boolean leftLeaningBalanced(@Nullable Node n, @Unbounded int blacks, @Unbounded int height) {
        reads(everything());
        decreases(height);
        if (n == null) {
            return blacks == 0;
        }
        if (height <= 0 || isRed(n.right) || (n.red && isRed(n.left))) {
            return false;
        }
        @Unbounded int below = n.red ? blacks : blacks - 1;
        return leftLeaningBalanced(n.left, below, height - 1) && leftLeaningBalanced(n.right, below, height - 1);
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

    static Node rotateLeft(Node h, @Unbounded int lo, @Unbounded int hi) {
        precondition(isRed(h.right));
        precondition(orderedN(h, lo, hi));
        precondition(h.right != h && h.right.left != h);
        modifies(everything());
        postcondition((Node res) -> res == old(h.right));
        postcondition((Node res) -> res.red == old(h.red));
        postcondition((Node res) -> res.key == old(h.right.key) && res.right == old(h.right.right));
        postcondition((Node res) -> res.left == h && h.red && h.key == old(h.key)
                && h.left == old(h.left) && h.right == old(h.right.left));
        postcondition((Node res) -> orderedN(res, lo, hi));
        Node x = h.right;
        h.right = x.left;
        x.left = h;
        x.red = h.red;
        h.red = true;
        x.size = h.size;
        h.size = 1 + size(h.left) + size(h.right);
        return x;
    }

    static Node rotateRight(Node h, @Unbounded int lo, @Unbounded int hi) {
        precondition(isRed(h.left));
        precondition(orderedN(h, lo, hi));
        precondition(h.left != h && h.left.right != h);
        modifies(everything());
        postcondition((Node res) -> res == old(h.left));
        postcondition((Node res) -> res.red == old(h.red));
        postcondition((Node res) -> res.key == old(h.left.key) && res.left == old(h.left.left));
        postcondition((Node res) -> res.right == h && h.red && h.key == old(h.key)
                && h.right == old(h.right) && h.left == old(h.left.right));
        postcondition((Node res) -> orderedN(res, lo, hi));
        Node x = h.left;
        h.left = x.right;
        x.right = h;
        x.red = h.red;
        h.red = true;
        x.size = h.size;
        h.size = 1 + size(h.left) + size(h.right);
        return x;
    }

    static void flipColors(Node h, @Unbounded int lo, @Unbounded int hi) {
        precondition(h.left != null && h.right != null);
        precondition(orderedN(h, lo, hi));
        precondition(h.left != h && h.right != h && h.left != h.right);
        modifies(everything());
        postcondition(h.red == !old(h.red) && h.key == old(h.key));
        postcondition(h.left == old(h.left) && h.right == old(h.right));
        postcondition(implies(old(h.left) != old(h.right) && old(h.left) != h && old(h.right) != h,
                h.left.red == !old(h.left.red) && h.right.red == !old(h.right.red)));
        postcondition(orderedN(h, lo, hi));
        h.red = !h.red;
        h.left.red = !h.left.red;
        h.right.red = !h.right.red;
    }

    static Node fixUp(Node h, @Unbounded int lo, @Unbounded int hi) {
        precondition(orderedN(h, lo, hi));
        modifies(everything());
        postcondition((Node res) -> orderedN(res, lo, hi));
        if (isRed(h.right) && !isRed(h.left)) {
            h = rotateLeft(h, lo, hi);
        }
        if (isRed(h.left) && isRed(h.left.left)) {
            h = rotateRight(h, lo, hi);
        }
        if (isRed(h.left) && isRed(h.right)) {
            flipColors(h, lo, hi);
        }
        return h;
    }

    static Node insertAt(@Nullable Node h, int key, @Unbounded int lo, @Unbounded int hi, @Unbounded int height) {
        precondition(orderedN(h, lo, hi) && lo < key && key < hi);
        modifies(everything());
        decreases(height);
        postcondition((Node res) -> orderedN(res, lo, hi));
        if (h == null) {
            Node n = new Node();
            n.key = key;
            n.red = true;
            n.size = 1;
            return n;
        }
        if (key < h.key) {
            h.left = insertAt(h.left, key, lo, h.key, height - 1);
        } else if (key > h.key) {
            h.right = insertAt(h.right, key, h.key, hi, height - 1);
        }
        h.size = 1 + size(h.left) + size(h.right);
        return fixUp(h, lo, hi);
    }

    static Node insert(@Nullable Node root, int key, @Unbounded int blacks, @Unbounded int height) {
        precondition(-2147483648 < key && key < 2147483647);
        precondition(orderedN(root, -2147483648, 2147483647));
        precondition(!isRed(root) && leftLeaningBalanced(root, blacks, height));
        modifies(everything());
        postcondition((Node res) -> orderedN(res, -2147483648, 2147483647));
        postcondition((Node res) -> !isRed(res)
                && (leftLeaningBalanced(res, blacks, height + 1) || leftLeaningBalanced(res, blacks + 1, height + 1)));
        postcondition((Node res) -> inTree(res, key, height + 1));

        Node r = insertAt(root, key, -2147483648, 2147483647, height);
        r.red = false;
        return r;
    }

    static Node moveRedLeft(Node h, @Unbounded int lo, @Unbounded int hi) {
        precondition(h.left != null && h.right != null);
        precondition(orderedN(h, lo, hi));
        modifies(everything());
        postcondition((Node res) -> orderedN(res, lo, hi));
        flipColors(h, lo, hi);
        if (isRed(h.right.left)) {
            h.right = rotateRight(h.right, h.key, hi);
            h = rotateLeft(h, lo, hi);
            flipColors(h, lo, hi);
        }
        return h;
    }

    static Node moveRedRight(Node h, @Unbounded int lo, @Unbounded int hi) {
        precondition(h.left != null && h.right != null);
        precondition(orderedN(h, lo, hi));
        modifies(everything());
        postcondition((Node res) -> orderedN(res, lo, hi));
        flipColors(h, lo, hi);
        if (isRed(h.left.left)) {
            h = rotateRight(h, lo, hi);
            flipColors(h, lo, hi);
        }
        return h;
    }

    static Node minNode(Node h, @Unbounded int height) {
        decreases(height);
        if (h.left == null) {
            return h;
        }
        return minNode(h.left, height - 1);
    }

    static @Nullable Node deleteMin(Node h, @Unbounded int lo, @Unbounded int hi, @Unbounded int height) {
        precondition(orderedN(h, lo, hi));
        modifies(everything());
        decreases(height);
        postcondition((Node res) -> orderedN(res, lo, hi));
        if (h.left == null) {
            return null;
        }
        if (!isRed(h.left) && !isRed(h.left.left)) {
            h = moveRedLeft(h, lo, hi);
        }
        h.left = deleteMin(h.left, lo, h.key, height - 1);
        h.size = 1 + size(h.left) + size(h.right);
        return fixUp(h, lo, hi);
    }

    static @Nullable Node deleteAt(Node h, int key, @Unbounded int lo, @Unbounded int hi, @Unbounded int height) {
        precondition(orderedN(h, lo, hi));
        modifies(everything());
        decreases(height);
        postcondition((Node res) -> orderedN(res, lo, hi));
        if (key < h.key) {
            if (h.left != null) {
                if (!isRed(h.left) && !isRed(h.left.left)) {
                    h = moveRedLeft(h, lo, hi);
                }
                h.left = deleteAt(h.left, key, lo, h.key, height - 1);
            }
        } else {
            if (isRed(h.left)) {
                h = rotateRight(h, lo, hi);
            }
            if (key == h.key && h.right == null) {
                return null;
            }
            if (h.right != null) {
                if (!isRed(h.right) && !isRed(h.right.left)) {
                    h = moveRedRight(h, lo, hi);
                }
                if (key == h.key) {
                    Node m = minNode(h.right, height - 1);
                    h.key = m.key;
                    h.right = deleteMin(h.right, h.key, hi, height - 1);
                } else {
                    h.right = deleteAt(h.right, key, h.key, hi, height - 1);
                }
            }
        }
        h.size = 1 + size(h.left) + size(h.right);
        return fixUp(h, lo, hi);
    }

    static @Nullable Node delete(Node root, int key, @Unbounded int blacks, @Unbounded int height) {
        precondition(orderedN(root, -2147483648, 2147483647));
        precondition(!isRed(root) && leftLeaningBalanced(root, blacks, height));
        modifies(everything());
        postcondition((Node res) -> orderedN(res, -2147483648, 2147483647));
        postcondition((Node res) -> !isRed(res)
                && (leftLeaningBalanced(res, blacks, height) || leftLeaningBalanced(res, blacks - 1, height)));
        postcondition((Node res) -> !inTree(res, key, height));

        if (!isRed(root.left) && !isRed(root.right)) {
            root.red = true;
        }
        @Nullable Node r = deleteAt(root, key, -2147483648, 2147483647, height);
        if (r != null) {
            r.red = false;
        }
        return r;
    }
}
