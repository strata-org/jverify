package com.aws.jverify.verifier;

class IntervalTreeNode {
    JavaMethodDetails method;
    int maxEnd;
    IntervalTreeNode left, right;

    IntervalTreeNode(JavaMethodDetails method) {
        this.method = method;
        this.maxEnd = method.getEndPosition().getLine();
    }
}

public class MethodIntervalTree {
    private IntervalTreeNode root;

    // Insert method with its line range
    public void insertMethod(JavaMethodDetails method) {
        root = insert(root, method);
    }

    private IntervalTreeNode insert(IntervalTreeNode node, JavaMethodDetails method) {
        if (node == null) return new IntervalTreeNode(method);

        if (method.getStartPosition().getLine() < node.method.getStartPosition().getLine()) {
            node.left = insert(node.left, method);
        } else {
            node.right = insert(node.right, method);
        }

        node.maxEnd = Math.max(node.maxEnd, method.getEndPosition().getLine());
        return node;
    }

    public JavaMethodDetails findMethodAtLine(int lineNumber) {
        return search(root, lineNumber);
    }

    private JavaMethodDetails search(IntervalTreeNode node, int lineNumber) {
        if (node == null) return null;

        // Check if current method contains the line
        if (lineNumber >= node.method.getStartPosition().getLine() && lineNumber <= node.method.getEndPosition().getLine()) {
            return node.method;
        }

        // Search left if possible
        if (node.left != null && node.left.maxEnd >= lineNumber) {
            JavaMethodDetails result = search(node.left, lineNumber);
            if (result != null) return result;
        }

        // Search right if line number is after current method start
        if (node.right != null && lineNumber >= node.method.getStartPosition().getLine()) {
            return search(node.right, lineNumber);
        }

        return null;
    }


}
