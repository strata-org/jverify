package com.aws.jverify.verifier;


import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class IntervalTree<Key extends Comparable<Key>, Value> {
    private IntervalTreeNode root;
    private int size;

    public class IntervalTreeNode {
        Key start, end;
        Value value;
        Key maxEnd;
        IntervalTreeNode left, right;

        IntervalTreeNode(Key start, Key end, Value value) {
            this.start = start;
            this.end = end;
            this.value = value;
            this.maxEnd = end;
        }

        public Value getValue() {
            return value;
        }

    }

    public void insert(Key start, Key end, Value value) {
        root = insert(root, start, end, value);
        size++;
    }

    private IntervalTreeNode insert(IntervalTreeNode node, Key start, Key end, Value value) {
        if (node == null) return new IntervalTreeNode(start, end, value);

        if (start.compareTo(node.start) < 0) {
            node.left = insert(node.left, start, end, value);
        } else {
            node.right = insert(node.right, start, end, value);
        }

        if (end.compareTo(node.maxEnd) > 0) {
            node.maxEnd = end;
        }
        return node;
    }

    public Value findAtPoint(Key point) {
        return search(root, point);
    }

    private Value search(IntervalTreeNode node, Key point) {
        if (node == null) return null;

        if (point.compareTo(node.start) >= 0 && point.compareTo(node.end) <= 0) {
            return node.value;
        }

        if (node.left != null && node.left.maxEnd.compareTo(point) >= 0) {
            Value result = search(node.left, point);
            if (result != null) return result;
        }

        if (node.right != null && point.compareTo(node.start) >= 0) {
            return search(node.right, point);
        }

        return null;
    }

    public Stream<IntervalTreeNode> streamNodes() {
        List<IntervalTreeNode> nodes = new ArrayList<>();
        collectNodes(root, nodes);
        return nodes.stream();
    }

    private void collectNodes(IntervalTreeNode node, List<IntervalTreeNode> nodes) {
        if (node != null) {
            nodes.add(node);
            collectNodes(node.left, nodes);
            collectNodes(node.right, nodes);
        }
    }

    public int getSize() {
        return size;
    }


}
