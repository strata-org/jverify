package com.aws.jverify.verifier.compiler.simplifications;

import com.aws.jverify.verifier.compiler.dafnygenerator.NativeSymbols;
import com.aws.jverify.verifier.compiler.dafnygenerator.base.BaseDafnyGenerator;
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
import com.sun.tools.javac.util.Names;

import javax.lang.model.util.Elements;
import java.util.*;

import static com.aws.jverify.verifier.compiler.dafnygenerator.base.BaseDafnyGenerator.JVERIFY_CLASS;

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
    NativeSymbols nativeSymbols;
    Set<Symbol> staticSymbols = new HashSet<>();
    Map<Symbol.ClassSymbol, Symbol.ClassSymbol> classMap = new HashMap<>();

    public MoveStaticMethodsToStaticType(Context context) {
        this.names = Names.instance(context);
        maker = TreeMaker.instance(context);
        syms = Symtab.instance(context);
        enter = Enter.instance(context);
        index = JVerifyIndex.instance(context);
        elements = JavacElements.instance(context);
        nativeSymbols = NativeSymbols.instance(context);
    }

    public Set<JCTree.JCCompilationUnit> translate(Set<JCTree.JCCompilationUnit> compilationUnits) {
        for(var unit : compilationUnits) {
            moveStaticMethodsToSeparateClass(unit);
        }
        var referenceUpdater = new ReferenceUpdater();
        for(var unit : compilationUnits) {
            referenceUpdater.translate(unit);
        }
        return compilationUnits;
    }

    private void moveStaticMethodsToSeparateClass(JCTree.JCCompilationUnit unit) {
        List<JCTree> remainingDefs = unit.defs;
        while(!remainingDefs.isEmpty()) {
            var type = remainingDefs.head;
            remainingDefs = remainingDefs.tail;
            if (type instanceof JCTree.JCClassDecl classDecl) {
                var methodCollector = new StaticMethodCollector(classDecl);
                methodCollector.translate(type);
                if (!methodCollector.staticMembers.isEmpty()) {
                    maker.pos = classDecl.pos;
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
            staticClassSymbol = new Symbol.ClassSymbol(0, classDecl.sym.name.append(NameCompiler.sep, names.fromString("static")), classDecl.sym.packge());
            staticClassSymbol.members_field = Scope.WriteableScope.create(staticClassSymbol);
            Type.ClassType classType = new Type.ClassType(Type.noType, com.sun.tools.javac.util.List.nil(), staticClassSymbol);
            classType.supertype_field = Type.noType; //syms.objectType;
            staticClassSymbol.type = classType;
            classMap.put(classDecl.sym, staticClassSymbol);
            staticClassSymbol.sourcefile = classDecl.sym.sourcefile;
        }

        @Override
        public void visitClassDef(JCTree.JCClassDecl tree) {
            if (tree.sym.isEnum()) {
                super.visitClassDef(tree);
                return;
            }
            
            if (tree.sym.owner.type != Type.noType && tree.sym.outermostClass().fullname.contentEquals(JVERIFY_CLASS)) {
                super.visitClassDef(tree);
                return;
            }

            var members = tree.getMembers();
            var newMembers = new ArrayList<JCTree>();
            while(!members.isEmpty()) {
                var member = members.head;
                members = members.tail;
                if (member instanceof JCTree.JCMethodDecl methodDecl) {
                    if (methodDecl.sym.isStatic() && !nativeSymbols.isRegistered(methodDecl.sym)) {
                        staticMembers = staticMembers.append(methodDecl);
                        var methodSymbol = methodDecl.sym;

                        staticSymbols.add(methodSymbol);
                        methodSymbol.owner = staticClassSymbol;
                        staticClassSymbol.members().enter(methodSymbol);
                        continue;
                    }
                } else if (member instanceof JCTree.JCVariableDecl varDecl) {
                    if (JVerifyUtils.isStatic(varDecl.mods) && !nativeSymbols.isRegistered(varDecl.sym)) {
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

    class ReferenceUpdater extends TreeTranslator {
        boolean staticContext = false;

        @Override
        public void visitSelect(JCTree.JCFieldAccess tree) {
            if (staticSymbols.contains(tree.sym)) {
                var previous = staticContext;
                staticContext = true;
                super.visitSelect(tree);
                staticContext = previous;
                tree.name = tree.sym.name;
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
            if (staticSymbols.contains(tree.sym)) {
                var previous = staticContext;
                staticContext = true;
                super.visitReference(tree);
                staticContext = previous;
                tree.name = tree.sym.name;
            } else {
                super.visitReference(tree);
            }
        }

        @Override
        public void visitIdent(JCTree.JCIdent tree) {
            if (staticSymbols.contains(tree.sym)) {
                // reference to same class static, qualify with new type
                maker.pos = tree.pos;
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