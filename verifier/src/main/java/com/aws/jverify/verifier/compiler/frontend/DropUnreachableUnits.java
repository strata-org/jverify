package com.aws.jverify.verifier.compiler.frontend;

import com.aws.jverify.verifier.VerifierOptions;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeScanner;
import com.sun.tools.javac.util.Context;

import java.util.*;

/**
 * When applying a file filter, so files become unused. This pass determines which files are unused and stops compiling them.
 */
public class DropUnreachableUnits {
    private final Map<Symbol, JCTree.JCCompilationUnit> symbolsToCompilationUnits = new HashMap<>();
    private final Map<JCTree.JCCompilationUnit, Set<JCTree.JCCompilationUnit>> dependencyCache = new HashMap<>();
    private final VerifierOptions options;

    public DropUnreachableUnits(Context context) {
        this.options = context.get(VerifierOptions.class);
    }
    
    public Set<JCTree.JCCompilationUnit> transform(Set<JCTree.JCCompilationUnit> compilationUnits) {
        var typeCollector = new TypeCollector();
        for(var unit : compilationUnits) {
            typeCollector.visitTopLevel(unit);
        }
        return findAllDependencies(compilationUnits);
    }
    
    class TypeCollector extends TreeScanner {
        JCTree.JCCompilationUnit compilationUnit;
        @Override
        public void visitTopLevel(JCTree.JCCompilationUnit tree) {
            compilationUnit = tree;
            super.visitTopLevel(tree);
        }

        @Override
        public void visitClassDef(JCTree.JCClassDecl tree) {
            symbolsToCompilationUnits.put(tree.sym, compilationUnit);
            super.visitClassDef(tree);
        }
    }

    public Set<JCTree.JCCompilationUnit> findAllDependencies(Set<JCTree.JCCompilationUnit> units) {
        var allDependencies = new LinkedHashSet<JCTree.JCCompilationUnit>();
        var visited = new HashSet<JCTree.JCCompilationUnit>();
        for(var unit : units) {
            if (options.positionFilter().unitPasses(options, unit)) {
                allDependencies.add(unit);
                findDependenciesRecursive(unit, allDependencies, visited);
            }
        }
        return allDependencies;
    }

    private void findDependenciesRecursive(JCTree.JCCompilationUnit unit,
                                           Set<JCTree.JCCompilationUnit> allDependencies,
                                           Set<JCTree.JCCompilationUnit> visited) {
        if (!visited.add(unit)) {
            return;
        }

        Set<JCTree.JCCompilationUnit> directDeps = dependencyCache.get(unit);
        if (directDeps == null) {
            directDeps = scanForDirectDependencies(unit);
            dependencyCache.put(unit, directDeps);
        }

        for (JCTree.JCCompilationUnit dependency : directDeps) {
            allDependencies.add(dependency);
            findDependenciesRecursive(dependency, allDependencies, visited);
        }
    }

    private Set<JCTree.JCCompilationUnit> scanForDirectDependencies(JCTree.JCCompilationUnit unit) {
        DependencyVisitor visitor = new DependencyVisitor();
        unit.accept(visitor);
        return visitor.getDependencies();
    }

    private class DependencyVisitor extends TreeScanner {
        private final Set<JCTree.JCCompilationUnit> dependencies = new LinkedHashSet<>();

        @Override
        public void visitIdent(JCTree.JCIdent tree) {
            addDependency(tree.sym);
            super.visitIdent(tree);
        }

        @Override
        public void visitSelect(JCTree.JCFieldAccess tree) {
            addDependency(tree.sym);
            super.visitSelect(tree);
        }

        private void addDependency(Symbol symbol) {
            if (symbol == null) {
                return;
            }
            var symbolUnit = symbolsToCompilationUnits.get(symbol);
            if (symbolUnit == null) {
                return;
            }
            dependencies.add(symbolUnit);
        }

        Set<JCTree.JCCompilationUnit> getDependencies() {
            return dependencies;
        }
    }
}
