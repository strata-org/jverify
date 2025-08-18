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
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.*;

public class MoveStaticMethodsToStaticType {
    
    Names names;
    TreeMaker maker;
    Symtab syms;
    Set<Symbol.MethodSymbol> staticMethodSymbols = new HashSet<>();

    public MoveStaticMethodsToStaticType(Context context) {
        this.names = Names.instance(context);
        maker = TreeMaker.instance(context);
        syms = Symtab.instance(context);
    }

    public Set<JCTree.JCCompilationUnit> translate(Set<JCTree.JCCompilationUnit> compilationUnits) {
        for(var unit : compilationUnits) {
            var methodCollector = new StaticMethodCollector(unit);
            methodCollector.translate(unit);
            JCTree.JCClassDecl staticClass = maker.ClassDef(maker.Modifiers(0), methodCollector.staticClassSymbol.name,
                    List.nil(), null, com.sun.tools.javac.util.List.nil(),
                    com.sun.tools.javac.util.List.from(methodCollector.staticMethods));
            staticClass.sym = methodCollector.staticClassSymbol;
            unit.defs = unit.defs.append(staticClass);
        }
        var referenceUpdater = new ReferenceUpdater();
        for(var unit : compilationUnits) {
            referenceUpdater.translate(unit);
        }
        return compilationUnits;
    }

    class StaticMethodCollector extends TreeTranslator {

        List<JCTree.JCMethodDecl> staticMethods = List.nil();
        JCTree.JCCompilationUnit currentUnit;
        Symbol.ClassSymbol staticClassSymbol;

        public StaticMethodCollector(JCTree.JCCompilationUnit compilationUnit) {
            staticClassSymbol = new Symbol.ClassSymbol(0, names.fromString("staticClass"), compilationUnit.packge);
            staticClassSymbol.members_field = Scope.WriteableScope.create(staticClassSymbol);
            Type.ClassType classType = new Type.ClassType(Type.noType, com.sun.tools.javac.util.List.nil(), staticClassSymbol);
            classType.supertype_field = syms.objectType;
            staticClassSymbol.type = classType;
        }

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
        Symbol.@Nullable ClassSymbol staticContext = null;
        
        @Override
        public void visitSelect(JCTree.JCFieldAccess tree) {
            if (tree.sym instanceof Symbol.MethodSymbol methodSymbol && staticMethodSymbols.contains(methodSymbol)) {
                var previous = staticContext;
                staticContext = (Symbol.ClassSymbol)methodSymbol.owner;
                super.visitSelect(tree);
                staticContext = previous;
                tree.name = tree.sym.name;
            } else {
                super.visitSelect(tree);
            }
        }

        @Override
        public void visitIdent(JCTree.JCIdent tree) {
            if (staticContext != null && tree.sym instanceof Symbol.ClassSymbol) {
                tree.sym = staticContext;
                tree.name = tree.sym.name;
            }
            super.visitIdent(tree);
        }
    }
}
