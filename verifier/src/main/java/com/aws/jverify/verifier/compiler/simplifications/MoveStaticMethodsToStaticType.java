package com.aws.jverify.verifier.compiler.simplifications;

import com.sun.tools.javac.code.Scope;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Names;
import java.util.*;

public class MoveStaticMethodsToStaticType {
    Map<JCTree, JCTree.JCCompilationUnit> originalCompilationUnit = new HashMap<>();
    
    Names names;
    Symbol.ClassSymbol staticClassSymbol;
    TreeMaker maker;
    Symtab syms;
    Set<Symbol.MethodSymbol> staticMethodSymbols = new HashSet<>();

    public MoveStaticMethodsToStaticType(Context context) {
        this.names = Names.instance(context);
        maker = TreeMaker.instance(context);
        syms = Symtab.instance(context);
    }

    public Set<JCTree.JCCompilationUnit> translate(Set<JCTree.JCCompilationUnit> compilationUnits) {
        JCTree.JCCompilationUnit receiver = compilationUnits.iterator().next();
        Symbol.PackageSymbol theftPackage = receiver.packge;
        
        var methodCollector = new StaticMethodCollector();
        staticClassSymbol = new Symbol.ClassSymbol(0, names.fromString("staticClass"), theftPackage);
        staticClassSymbol.members_field = Scope.WriteableScope.create(staticClassSymbol);
        Type.ClassType classType = new Type.ClassType(Type.noType, com.sun.tools.javac.util.List.nil(), staticClassSymbol);
        classType.supertype_field = syms.objectType;
        
        staticClassSymbol.type = classType;
        for(var unit : compilationUnits) {
            methodCollector.translate(unit);
        }
        var referenceUpdater = new ReferenceUpdater();
        for(var unit : compilationUnits) {
            referenceUpdater.translate(unit);
        }
        JCTree.JCClassDecl staticClass = maker.ClassDef(maker.Modifiers(0), staticClassSymbol.name,
            List.nil(), null, com.sun.tools.javac.util.List.nil(),
                com.sun.tools.javac.util.List.from(methodCollector.staticMethods));
        staticClass.sym = staticClassSymbol;

        receiver.defs = receiver.defs.append(staticClass);
        return compilationUnits;
    }

    class StaticMethodCollector extends TreeTranslator {

        List<JCTree.JCMethodDecl> staticMethods = List.nil();
        JCTree.JCCompilationUnit currentUnit;

        @Override
        public void visitTopLevel(JCTree.JCCompilationUnit tree) {
            currentUnit = tree;
            super.visitTopLevel(tree);
        }

        @Override
        public void visitClassDef(JCTree.JCClassDecl tree) {
            var members = tree.getMembers();
            var newMembers = new ArrayList<JCTree>();
            while(!members.isEmpty()) {
                var member = members.head;
                members = members.tail;
                if (member instanceof JCTree.JCMethodDecl methodDecl) {
                    if (methodDecl.sym.isStatic()) {
                        staticMethods = staticMethods.append(methodDecl);
                        var sym = methodDecl.sym;
                        staticMethodSymbols.add(sym);
                        originalCompilationUnit.put(methodDecl, currentUnit);
                        staticClassSymbol.members().enter(sym);
                        sym.owner = staticClassSymbol;
                        sym.name = tree.sym.fullname.append('.', sym.name);
                        continue;
                    }
                }
                newMembers.add(member);
            }
            tree.defs = List.from(newMembers);
            super.visitClassDef(tree);
        }
        
    }

    class ReferenceUpdater extends TreeTranslator {
        boolean staticContext = false;
        
        @Override
        public void visitSelect(JCTree.JCFieldAccess tree) {
            if (tree.sym instanceof Symbol.MethodSymbol methodSymbol && staticMethodSymbols.contains(methodSymbol)) {
                var previous = staticContext;
                staticContext = true;
                super.visitSelect(tree);
                staticContext = previous;
                tree.name = tree.sym.name;
            } else {
                super.visitSelect(tree);
            }
        }

        @Override
        public void visitIdent(JCTree.JCIdent tree) {
            if (staticContext && tree.sym instanceof Symbol.ClassSymbol) {
                tree.sym = staticClassSymbol;
                tree.name = tree.sym.name;
            }
            super.visitIdent(tree);
        }
    }
}
