package com.aws.jverify.examples;

import static com.aws.jverify.JVerify.*;
import com.aws.jverify.*;

/**
 * A verified AVL tree implementation using JVerify.
 * An AVL tree is a self-balancing binary search tree where the heights
 * of the two child subtrees of any node differ by at most one.
 */
@Modifiable
public class VerifiedAVLTree {
    private @Nullable Node root;
    
    public VerifiedAVLTree() {
        postcondition(forall((int x) -> !contains(x)));
        postcondition(valid());
        this.root = null;
    }
    
    /**
     * Class invariant: the tree maintains AVL property and BST property
     */
    @Erased
    @Pure
    private boolean valid() {
        reads(this);
        return root == null || root.valid();
    }
    
    /**
     * Insert a value into the AVL tree
     */
    public void insert(int value) {
        modifies(this);
        precondition(this.valid());
        postcondition(this.valid());
        postcondition(forall((int x) -> x == value ? contains(x) : old(contains(x)) == contains(x)));
        
        root = insertHelper(root, value);
    }
    
    /**
     * Check if the tree contains a value
     */
    @Pure
    public boolean contains(int value) {
        reads(this);
        return containsHelper(root, value);
    }
    
    /**
     * Get the height of the tree
     */
    @Pure
    public int getHeight() {
        reads(this);
        return getHeightHelper(root);
    }
    
    /**
     * Helper method for insertion with rebalancing
     */
    private @Nullable Node insertHelper(@Nullable Node node, int value) {
        
        postcondition((@Nullable Node result) -> 
            result.valid() && 
            forall((int x) -> x == value ? containsHelper(result, x) : old(containsHelper(node, x)) == containsHelper(result, x))
        );
        modifies(node.left);
        modifies(node.right);
        
        // Base case: create new node
        if (node == null) {
            return new Node(value);
        }
        
        // Recursive insertion
        if (value < node.value) {
            node.left = insertHelper(node.left, value);
        } else if (value > node.value) {
            node.right = insertHelper(node.right, value);
        } else {
            // Value already exists, no change needed
            return node;
        }
        
        // Update height
        node.height = 1 + Math.max(getHeightHelper(node.left), getHeightHelper(node.right));
        
        // Get balance factor
        @Unbounded int balance = getBalance(node);
        
        // Perform rotations if needed
        // Left Left Case
        if (balance > 1 && value < node.left.value) {
            return rotateRight(node);
        }
        
        // Right Right Case
        if (balance < -1 && value > node.right.value) {
            return rotateLeft(node);
        }
        
        // Left Right Case
        if (balance > 1 && value > node.left.value) {
            node.left = rotateLeft(node.left);
            return rotateRight(node);
        }
        
        // Right Left Case
        if (balance < -1 && value < node.right.value) {
            node.right = rotateRight(node.right);
            return rotateLeft(node);
        }
        
        return node;
    }

    /**
     * Helper method to check if tree contains a value
     */
    @Pure
    @Erased
    private boolean containsHelper(@Nullable Node node, int value) {
        precondition(node.validHeight());
        decreases(node.height);
        return node == null 
                ? false : (value == node.value ? true :
                (value < node.value ? containsHelper(node.left, value) : containsHelper(node.right, value)));
    }
    
    /**
     * Get height of a node (null nodes have height 0)
     */
    @Pure
    @Erased
    @Unbounded
    private static int getHeightHelper(@Nullable Node node) 
    {
        reads(node);
        return node == null ? 0 : node.height;
    }
    
    /**
     * Get balance factor of a node
     */
    @Pure
    @Erased
    @Unbounded
    private static int getBalance(@Nullable Node node) {
        reads(node);
        reads(node.left);
        reads(node.right);
        return node == null ? 0 : getHeightHelper(node.left) - getHeightHelper(node.right);
    }
    
    /**
     * Right rotate operation
     */
    private Node rotateRight(Node y) {
        precondition(y.left != null && y.left.right != null);
        postcondition((Node result) -> 
            isValidAVL(result) &&
            forall((int x) -> containsHelper(y, x) == containsHelper(result, x))
        );
        modifies(y);
        modifies(y.left);
        
        Node x = y.left;
        Node T2 = x.right;
        
        // Perform rotation
        x.right = y;
        y.left = T2;
        
        // Update heights
        y.height = Math.max(getHeightHelper(y.left), getHeightHelper(y.right)) + 1;
        x.height = Math.max(getHeightHelper(x.left), getHeightHelper(x.right)) + 1;
        
        check(isValidAVL(y));
        check(Math.abs(getBalance(x)) <= 1 &&
           isValidAVL(x.left) &&
           isValidAVL(x.right) &&
           x.height == 1 + Math.max(getHeightHelper(x.left), getHeightHelper(x.right)));

        return x;
    }
    
    /**
     * Left rotate operation
     */
    private Node rotateLeft(Node x) {
        precondition(x.right != null && x.right.left != null);
        postcondition((Node result) -> 
            isValidAVL(result) &&
            forall((int x_val) -> containsHelper(x, x_val) == containsHelper(result, x_val))
        );
        modifies(x);
        modifies(x.right);
        
        Node y = x.right;
        Node T2 = y.left;
        
        // Perform rotation
        y.left = x;
        x.right = T2;
        
        // Update heights
        x.height = Math.max(getHeightHelper(x.left), getHeightHelper(x.right)) + 1;
        y.height = Math.max(getHeightHelper(y.left), getHeightHelper(y.right)) + 1;
        
        return y;
    }
    
    /**
     * Check if a tree rooted at node is a valid AVL tree
     */
    @Pure
    @Erased
    private static boolean isValidAVL(@Nullable Node node) {
        return node == null ? true : Math.abs(getBalance(node)) <= 1 &&
           isValidAVL(node.left) &&
           isValidAVL(node.right) &&
           node.height == 1 + Math.max(getHeightHelper(node.left), getHeightHelper(node.right));
    }
    
    /**
     * Check if a tree rooted at node satisfies BST property
     */
    @Pure
    @Erased
    private static boolean isBinarySearchTree(@Nullable Node node, int minVal, int maxVal) {
        decreases(node.height);
        return node == null ? true : node.validHeight() && minVal < node.value && 
               node.value < maxVal &&
               isBinarySearchTree(node.left, minVal, node.value) &&
               isBinarySearchTree(node.right, node.value, maxVal);
    }
    
    /**
     * Node class representing a tree node
     */
    private static class Node {
        @Unbounded int value;
        @Unbounded int height;
        @Nullable Node left;
        @Nullable Node right;
        
        public Node(int value) {
            postcondition(this.value == value);
            postcondition(this.height == 1);
            postcondition(this.left == null);
            postcondition(this.right == null);
            
            this.value = value;
            this.height = 1;
            this.left = null;
            this.right = null;
        }
        
        /**
         * Node invariant: height is correctly computed
         */
        @Erased
        @Pure
        @Invariant
        private boolean validHeight() {
            reads(this);
            reads(left);
            reads(right);
            return height == 1 + Math.max(
                left == null ? 0 : left.height,
                right == null ? 0 : right.height
            );
        }
    
        /**
         * Class invariant: the tree maintains AVL property and BST property
         */
        @Erased
        @Pure
        private boolean valid() {
            reads(this);
            return validHeight() && isValidAVL(this) && isBinarySearchTree(this, Integer.MIN_VALUE, Integer.MAX_VALUE);
        }
    }
    
    /**
     * Test method to demonstrate usage
     */
    public static void testAVLTree() {
        VerifiedAVLTree tree = new VerifiedAVLTree();
        
        // Insert some values
        check(!tree.contains(10));
        tree.insert(10);
        check(tree.contains(10));
        check(tree.getHeight() == 1);
        
        tree.insert(20);
        check(tree.contains(20));
        check(tree.contains(10));
        check(tree.contains(11));
        check(!tree.contains(30));
        
        tree.insert(30);
        check(tree.contains(30));
        check(tree.contains(20));
        check(tree.contains(10));
        
        // Tree should be balanced after these insertions
        check(tree.getHeight() <= 3);
        
        tree.insert(40);
        tree.insert(50);
        tree.insert(25);
        
        // Verify all elements are still present
        check(tree.contains(10));
        check(tree.contains(20));
        check(tree.contains(25));
        check(tree.contains(30));
        check(tree.contains(40));
        check(tree.contains(50));
        
        // Verify non-existent elements
        check(!tree.contains(15));
        check(!tree.contains(35));
    }
}

@Contract(Math.class)
class MathContract {
    @Pure
    @Unbounded 
    static int max(@Unbounded int a, @Unbounded int b) {
        // Postcondition should not be needed
        postcondition((@Unbounded int result) -> result == (a > b ? a : b));
        return a > b ? a : b;
    }
    
    @Pure
    @Unbounded 
    static int abs(@Unbounded int a) {
        return a < 0 ? -a : a;
    }
}