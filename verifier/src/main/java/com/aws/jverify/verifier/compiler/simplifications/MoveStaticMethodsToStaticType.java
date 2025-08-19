package com.aws.jverify.verifier.compiler.simplifications;

import com.aws.jverify.Contract;
import com.aws.jverify.Verify;
import com.aws.jverify.verifier.compiler.frontend.JavaFrontEnd;
import com.sun.tools.javac.code.Scope;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.model.JavacElements;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;
import org.checkerframework.checker.nullness.qual.Nullable;

import javax.lang.model.util.Elements;
import java.lang.annotation.Annotation;
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
    Set<Symbol.MethodSymbol> staticMethodSymbols = new HashSet<>();

    public MoveStaticMethodsToStaticType(Context context) {
        this.names = Names.instance(context);
        maker = TreeMaker.instance(context);
        syms = Symtab.instance(context);
        elements = JavacElements.instance(context);
    }

    public Set<JCTree.JCCompilationUnit> translate(Set<JCTree.JCCompilationUnit> compilationUnits) {
        for(var unit : compilationUnits) {
            List<JCTree> remainingDefs = unit.defs;
            while(!remainingDefs.isEmpty()) {
                var type = remainingDefs.head;
                remainingDefs = remainingDefs.tail;
                if (type instanceof JCTree.JCClassDecl classDecl) {
                    var methodCollector = new StaticMethodCollector(classDecl);
                    methodCollector.translate(type);
                    if (!methodCollector.staticMethods.isEmpty()) {
                        List<JCTree.JCAnnotation> annotations = List.nil();
                        if (classDecl.sym.getAnnotation(Contract.class) != null) {
                            var classTypeElement = elements.getTypeElement(Verify.class.getCanonicalName());
                            JCTree ident = maker.Ident((Symbol.ClassSymbol)classTypeElement);
                            annotations = annotations.append(maker.Annotation(ident,
                                    List.of(maker.Assign(maker.Ident(names.fromString("value")), maker.Literal(false)))));
                        }
                        JCTree.JCClassDecl staticClass = maker.ClassDef(maker.Modifiers(0, annotations), methodCollector.staticClassSymbol.name,
                                List.nil(), null, com.sun.tools.javac.util.List.nil(),
                                com.sun.tools.javac.util.List.from(methodCollector.staticMethods));
                        staticClass.sym = methodCollector.staticClassSymbol;
                        unit.defs = unit.defs.append(staticClass);
                    }
                }
            }
        }
        var referenceUpdater = new ReferenceUpdater();
        for(var unit : compilationUnits) {
            referenceUpdater.translate(unit);
        }
        return compilationUnits;
    }

    class StaticMethodCollector extends TreeTranslator {

        List<JCTree.JCMethodDecl> staticMethods = List.nil();
        Symbol.ClassSymbol staticClassSymbol;

        public StaticMethodCollector(JCTree.JCClassDecl classDecl) {
            staticClassSymbol = new Symbol.ClassSymbol(0, classDecl.name.append('.', names.fromString("static")), classDecl.sym.packge());
            staticClassSymbol.members_field = Scope.WriteableScope.create(staticClassSymbol);
            Type.ClassType classType = new Type.ClassType(Type.noType, com.sun.tools.javac.util.List.nil(), staticClassSymbol);
            classType.supertype_field = syms.objectType;
            staticClassSymbol.type = classType;
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
                        
                        // TODO does not work for contract methods
                        staticMethodSymbols.add(sym);
                        sym.name = tree.sym.fullname.append('.', sym.name);
                        sym.owner = staticClassSymbol;
                        staticClassSymbol.members().enter(sym);
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
