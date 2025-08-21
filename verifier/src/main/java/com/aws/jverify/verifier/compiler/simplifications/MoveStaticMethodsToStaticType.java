package com.aws.jverify.verifier.compiler.simplifications;

import com.aws.jverify.verifier.compiler.JavaToDafnyCompiler;
import com.aws.jverify.verifier.compiler.frontend.JVerifyIndex;
import com.sun.tools.javac.code.Scope;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.comp.Enter;
import com.sun.tools.javac.model.JavacElements;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;

import javax.lang.model.util.Elements;
import java.util.*;

/**
 * In Java, static methods ignore the type parameters of any enclosing types
 * We compile this behavior by moving static methods to a separate type that does not have type parameters 
 */
public class MoveStaticMethodsToStaticType {
    
    Names names;
    TreeMaker maker;
    Symtab syms;
    Elements elements;
    JVerifyIndex index;
    Enter enter;
    Set<Symbol> staticSymbols = new HashSet<>();
    Set<Symbol.TypeSymbol> containsStaticSymbols = new HashSet<>();
    Map<Symbol.ClassSymbol, Symbol.ClassSymbol> classMap = new HashMap<>();

    public MoveStaticMethodsToStaticType(Context context) {
        this.names = Names.instance(context);
        maker = TreeMaker.instance(context);
        syms = Symtab.instance(context);
        enter = Enter.instance(context);
        index = JVerifyIndex.instance(context);
        elements = JavacElements.instance(context);
    }

    public Set<JCTree.JCCompilationUnit> translate(Set<JCTree.JCCompilationUnit> compilationUnits) {
        var referenceUpdater = new ReferenceUpdater();
        for(var unit : compilationUnits) {
            referenceUpdater.translate(unit);
        }
        for(var unit : compilationUnits) {
            moveStaticMethodsToSeparateClass(unit);
        }
        return compilationUnits;
    }

    private void moveStaticMethodsToSeparateClass(JCTree.JCCompilationUnit unit) {
        List<JCTree> remainingDefs = unit.defs;
        while(!remainingDefs.isEmpty()) {
            var type = remainingDefs.head;
            remainingDefs = remainingDefs.tail;
            if (type instanceof JCTree.JCClassDecl classDecl) {
                if (!classMap.containsKey(classDecl.sym)) {
                    continue;
                }
                var methodCollector = new StaticMethodCollector(classDecl);
                methodCollector.translate(type);
                if (!methodCollector.staticMembers.isEmpty()) {
                    JCTree.JCClassDecl staticClass = maker.ClassDef(classDecl.mods, methodCollector.staticClassSymbol.name,
                            List.nil(), null, List.nil(),
                            List.from(methodCollector.staticMembers));
                    staticClass.sym = methodCollector.staticClassSymbol;
                    staticClass.type = new Type.ClassType(Type.noType, List.nil(), staticClass.sym, List.nil());
                    unit.defs = unit.defs.append(staticClass);
                    index.put(staticClass.sym, enter.classEnv(staticClass, enter.getTopLevelEnv(unit)));
                }
            }
        }
    }

    class StaticMethodCollector extends TreeTranslator {

        List<JCTree> staticMembers = List.nil();
        Symbol.ClassSymbol staticClassSymbol;

        public StaticMethodCollector(JCTree.JCClassDecl classDecl) {
            staticClassSymbol = classMap.get(classDecl.sym);
        }

        @Override
        public void visitClassDef(JCTree.JCClassDecl tree) {
            if (tree.sym.isEnum()) {
                super.visitClassDef(tree);
                return;
            }
            
            var members = tree.getMembers();
            var newMembers = new ArrayList<JCTree>();
            while(!members.isEmpty()) {
                var member = members.head;
                members = members.tail;
                if (member instanceof JCTree.JCMethodDecl methodDecl) {
                    if (methodDecl.sym.isStatic()) {
                        staticMembers = staticMembers.append(methodDecl);
                        var methodSymbol = methodDecl.sym;
                        
                        staticSymbols.add(methodSymbol);
                        methodSymbol.owner = staticClassSymbol;
                        staticClassSymbol.members().enter(methodSymbol);
                        continue;
                    }
                } else if (member instanceof JCTree.JCVariableDecl varDecl) {
                    if (JavaToDafnyCompiler.isStatic(varDecl.mods)) {
                        staticMembers = staticMembers.append(varDecl);
                        var varSymbol = varDecl.sym;

                        staticSymbols.add(varSymbol);
                        varSymbol.owner = staticClassSymbol;
                        staticClassSymbol.members().enter(varSymbol);
                        continue;
                    }
                }
                newMembers.add(member);
            }
            tree.defs = List.from(newMembers);
            super.visitClassDef(tree);
        }
        
    }

    Symbol.ClassSymbol registerStaticClass(Symbol.ClassSymbol classSymbol) {
        var result = classMap.get(classSymbol);
        if (result == null) {
            Name staticName = classSymbol.name.append(NameCompiler.sep, names.fromString("static"));
            var staticClassSymbol = new Symbol.ClassSymbol(0, staticName, classSymbol.packge());
            staticClassSymbol.members_field = Scope.WriteableScope.create(staticClassSymbol);
            Type.ClassType classType = new Type.ClassType(Type.noType, com.sun.tools.javac.util.List.nil(), staticClassSymbol);
            classType.supertype_field = Type.noType;
            staticClassSymbol.type = classType;
            classMap.put(classSymbol, staticClassSymbol);
            return staticClassSymbol;
        } 
        return result;
    }
    
    boolean registerStatic(Symbol classMemberSymbol) {
        if (classMemberSymbol.owner instanceof Symbol.ClassSymbol classSymbol) {
            classMemberSymbol.owner = registerStaticClass(classSymbol);
            return true;
        }
        return false;
    }

    class ReferenceUpdater extends TreeTranslator {
        boolean staticContext = false;
        
        @Override
        public void visitSelect(JCTree.JCFieldAccess tree) {
            if (tree.sym.isStatic()) {
                
                var previous = staticContext;
                staticContext = registerStatic(tree.sym);
                super.visitSelect(tree);
                staticContext = previous;
            } else {
                if (staticContext) {
                    var newSym = classMap.get(tree.sym);
                    if (newSym != null) {
                        tree.sym = newSym;
                        tree.name = tree.sym.name;
                    }
                }
                super.visitSelect(tree);
            }
        }

        @Override
        public void visitReference(JCTree.JCMemberReference tree) {
            if (tree.sym.isStatic()) {
                var previous = staticContext;
                staticContext = registerStatic(tree.sym);
                super.visitReference(tree);
                staticContext = previous;
                tree.name = tree.sym.name;
            } else {
                super.visitReference(tree);
            }
        }

        @Override
        public void visitIdent(JCTree.JCIdent tree) {
            if (tree.sym.isStatic()) {
                registerStatic(tree.sym);
                
                // reference to same class static, qualify with new type
                var select = maker.Select(maker.Ident(tree.sym.owner), tree.name);
                select.sym = tree.sym;
                select.type = tree.type;
                result = select;
            } else {
                if (staticContext) {
                    var newSym = classMap.get(tree.sym);
                    if (newSym != null) {
                        tree.sym = newSym;
                        tree.name = tree.sym.name;
                    }
                }
                super.visitIdent(tree);
            }
        }
    }
}
