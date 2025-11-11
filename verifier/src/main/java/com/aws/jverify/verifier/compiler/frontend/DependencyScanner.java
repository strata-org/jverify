package com.aws.jverify.verifier.compiler.frontend;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreeScanner;
import com.sun.source.util.Trees;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Types;

import javax.lang.model.util.Elements;
import java.util.*;

public class RecursiveDependencyScanner {
    private final Trees trees;
    private final Elements elements;
    private final Types types;
    private final Map<CompilationUnitTree, Set<CompilationUnitTree>> dependencyCache = new HashMap<>();

    public RecursiveDependencyScanner(Trees trees, Elements elements, Types types) {
        this.trees = trees;
        this.elements = elements;
        this.types = types;
    }

    public Set<CompilationUnitTree> findAllDependencies(CompilationUnitTree root) {
        Set<CompilationUnitTree> allDependencies = new LinkedHashSet<>();
        findDependenciesRecursive(root, allDependencies, new HashSet<>());
        allDependencies.remove(root); // Remove self if present
        return allDependencies;
    }

    private void findDependenciesRecursive(CompilationUnitTree unit,
                                           Set<CompilationUnitTree> allDependencies,
                                           Set<CompilationUnitTree> visited) {
        if (!visited.add(unit)) {
            return; // Already processed this unit
        }

        // Check cache first
        Set<CompilationUnitTree> directDeps = dependencyCache.get(unit);
        if (directDeps == null) {
            directDeps = scanForDirectDependencies(unit);
            dependencyCache.put(unit, directDeps);
        }

        for (CompilationUnitTree dependency : directDeps) {
            allDependencies.add(dependency);
            findDependenciesRecursive(dependency, allDependencies, visited);
        }
    }

    private Set<CompilationUnitTree> scanForDirectDependencies(CompilationUnitTree unit) {
        DependencyVisitor visitor = new DependencyVisitor(unit);
        unit.accept(visitor, null);
        return visitor.getDependencies();
    }

    private class DependencyVisitor extends TreeScanner<Void, Void> {
        private final Set<CompilationUnitTree> dependencies = new LinkedHashSet<>();
        private final CompilationUnitTree currentUnit;

        DependencyVisitor(CompilationUnitTree currentUnit) {
            this.currentUnit = currentUnit;
        }

        @Override
        public Void visitIdentifier(IdentifierTree node, Void unused) {
            addDependencyIfExternal(node);
            return super.visitIdentifier(node, unused);
        }

        @Override
        public Void visitMemberSelect(MemberSelectTree node, Void unused) {
            addDependencyIfExternal(node);
            return super.visitMemberSelect(node, unused);
        }

        private void addDependencyIfExternal(Tree node) {
            TreePath path = trees.getPath(currentUnit, node);
            if (path != null) {
                Symbol symbol = trees.getElement(path);
                if (symbol != null) {
                    TreePath symbolPath = trees.getPath(symbol);
                    if (symbolPath != null) {
                        CompilationUnitTree dependencyUnit = symbolPath.getCompilationUnit();
                        if (dependencyUnit != currentUnit) {
                            dependencies.add(dependencyUnit);
                        }
                    }
                }
            }
        }

        Set<CompilationUnitTree> getDependencies() {
            return dependencies;
        }
    }
}
