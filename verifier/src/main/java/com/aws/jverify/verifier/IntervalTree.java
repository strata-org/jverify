package com.aws.jverify.verifier;

import com.aws.jverify.verifier.compiler.dafnygenerator.base.BaseDafnyGenerator;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.stream.Collectors;

public class IntervalTree<Key extends Comparable<Key>, Value> {
    private IntervalTreeNode root;

    class IntervalTreeNode {
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
    }

    public void insert(Key start, Key end, Value value) {
        root = insert(root, start, end, value);
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


    public static HashMap<URI, IntervalTree<Integer, JavaMethodDetails>> getIntervalTreeMap(VerificationResults verificationResults) {
        HashMap<URI, IntervalTree<Integer, JavaMethodDetails>> intervalTreeMap = null;
        if (verificationResults.getJavaInSourceMethods() != null) {
            intervalTreeMap = getIntervalsFromVerifiableMethods(verificationResults.getJavaInSourceMethods());
        }
        return intervalTreeMap;
    }

    private static HashMap<URI,IntervalTree<Integer, JavaMethodDetails>> getIntervalsFromVerifiableMethods(ArrayList<JavaMethodDetails> javaInSourceMethods) {

        var distinctClasses = javaInSourceMethods.stream()
                .map(method -> method.getMethodTree().sym.owner.enclClass().sourcefile)
                .collect(Collectors.toSet());

        HashMap<URI,IntervalTree<Integer, JavaMethodDetails>> intervalTrees = new HashMap<>();
        for (var distinctClass : distinctClasses) {
            IntervalTree<Integer, JavaMethodDetails> methodIntervalTree = new IntervalTree<>();
            javaInSourceMethods.stream()
                    .filter(method -> !BaseDafnyGenerator.isSynthetic(method.getMethodTree().getModifiers().flags) && method.getMethodTree().sym.owner.enclClass().sourcefile.equals(distinctClass))
                    .forEach(method -> {
                        assert method.getPosition().getEndToken() != null;
                        methodIntervalTree.insert(method.getPosition().getStartToken().getLine(),
                                method.getPosition().getEndToken().getLine(), method);
                    });
            intervalTrees.put(distinctClass.toUri().normalize(), methodIntervalTree);
        }

        return intervalTrees;
    }


}
