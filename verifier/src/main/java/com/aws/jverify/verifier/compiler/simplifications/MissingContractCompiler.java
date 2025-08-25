package com.aws.jverify.verifier.compiler.simplifications;

import com.aws.jverify.Pure;
import com.aws.jverify.verifier.compiler.Reporter;
import com.aws.jverify.verifier.compiler.frontend.JVerifyIndex;
import com.sun.tools.javac.code.*;
import com.sun.tools.javac.comp.Enter;
import com.sun.tools.javac.model.JavacElements;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeScanner;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.JCDiagnostic;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Names;

import javax.lang.model.type.TypeKind;
import java.util.*;

import static com.aws.jverify.verifier.compiler.JavaToDafnyCompiler.JVERIFY_CLASS;

public class MissingContractCompiler {

    private final TreeMaker maker;
    private final JVerifyIndex index;
    private final Enter enter;
    private final Names names;
    private final Symtab symtab;
    private final JavacElements elements;
    private final JVerifyMaker verifyMaker;

    private final Map<Symbol, Reference> symbolReferences = new HashMap<>();
    private final Set<Symbol> foundSymbols = new HashSet<>();
    private final Reporter reporter;

    record Reference(Symbol symbol, JCTree.JCCompilationUnit compilationUnit, JCTree tree) {}
    
    public MissingContractCompiler(Context context) {
        this.maker = TreeMaker.instance(context);
        this.index = JVerifyIndex.instance(context);
        this.names = Names.instance(context);
        this.enter = Enter.instance(context);
        this.elements = JavacElements.instance(context);
        this.symtab = Symtab.instance(context);
        verifyMaker = JVerifyMaker.instance(context);
        reporter = Reporter.instance(context);
    }
    
    public Set<JCTree.JCCompilationUnit> compile(Set<JCTree.JCCompilationUnit> units) {
        var finder = new SymbolReferenceFinder();
        for(var unit : units) {
            finder.visitTopLevel(unit);
        }
        addMissingSymbols();
        return units;
    }
    
    void addMissingSymbols() {
        for(var entry : symbolReferences.entrySet()) {
            if (foundSymbols.contains(entry.getKey())) {
                continue;
            }
            
            var symbol = entry.getValue().symbol;
            if (symbol instanceof Symbol.ClassSymbol classSymbol) {
                getOrAddType(classSymbol, entry.getValue());
            } else if (symbol instanceof Symbol.MethodSymbol methodSymbol) {
                addMissingMethod(entry.getValue(), methodSymbol);
            }
        }
    }

    private void addMissingMethod(Reference reference, Symbol.MethodSymbol methodSymbol) {
        maker.pos = reference.tree.pos;
        var methodDecl = (JCTree.JCMethodDecl)index.getTree(methodSymbol);
        if (methodDecl == null) {
            var classDecl = getOrAddType(methodSymbol.enclClass(), reference);

            methodDecl = maker.MethodDef(methodSymbol, maker.Block(0, com.sun.tools.javac.util.List.nil()));
            classDecl.defs = classDecl.defs.append(methodDecl);
        } else {
            methodDecl.body = maker.Block(0, com.sun.tools.javac.util.List.nil());
        }
        Symbol.ClassSymbol pureSymbol = elements.getTypeElement(Pure.class.getCanonicalName());
        if (methodSymbol.type.getReturnType().getKind() != TypeKind.VOID) {
            methodDecl.mods.annotations = methodDecl.mods.annotations.append(
                    maker.Annotation(maker.Ident(pureSymbol), com.sun.tools.javac.util.List.nil()));
        }
        methodDecl.mods.annotations = methodDecl.mods.annotations.append(verifyMaker.getVerifyFalseAnnotation());

        reporter.compilationUnit = reference.compilationUnit();
        reporter.reportDiagnostic(reporter.toOrigin(reference.tree), JCDiagnostic.DiagnosticType.WARNING, "missingContract",
                methodSymbol.getQualifiedName(), methodSymbol.enclClass().getQualifiedName());
    }

    private JCTree.JCClassDecl getOrAddType(Symbol.ClassSymbol classSymbol, Reference reference) {
        var compilationUnit = reference.compilationUnit();
        if (!foundSymbols.add(classSymbol)) {
            return (JCTree.JCClassDecl) index.getTree(classSymbol);
        }
        maker.pos = reference.tree.pos;
        List<JCTree.JCTypeParameter> typeParameters = classSymbol.getTypeParameters().map(tp ->
                maker.TypeParameter(tp.name, tp.getBounds().map(maker::Type)));
        JCTree.JCExpression superClassExpr = classSymbol.getSuperclass() == Type.noType ? null : maker.Type(classSymbol.getSuperclass());
        JCTree.JCClassDecl classDecl = maker.ClassDef(maker.Modifiers(0),
                classSymbol.name, typeParameters, superClassExpr,
                classSymbol.getInterfaces().map(maker::Type), List.nil());
        compilationUnit.defs = compilationUnit.defs.append(classDecl);
        classDecl.sym = classSymbol;
        classDecl.type = classSymbol.type;
        index.put(classSymbol, enter.classEnv(classDecl, enter.getTopLevelEnv(compilationUnit)));

        reporter.compilationUnit = compilationUnit;
        return classDecl;
    }

    class SymbolReferenceFinder extends TreeScanner {
        
        JCTree.JCCompilationUnit compilationUnit;
        @Override
        public void visitTopLevel(JCTree.JCCompilationUnit tree) {
            compilationUnit = tree;
            super.visitTopLevel(tree);
        }

        @Override
        public void visitClassDef(JCTree.JCClassDecl tree) {
            foundSymbols.add(tree.sym);
            if (tree.sym.isEnum()) {
                return;
            }
            super.visitClassDef(tree);
        }

        @Override
        public void visitMethodDef(JCTree.JCMethodDecl tree) {
            if (tree.body != null) {
                foundSymbols.add(tree.sym);
            }
            visitType(tree.type, tree);
            super.visitMethodDef(tree);
        }

        @Override
        public void visitAnnotation(JCTree.JCAnnotation tree) {
            // skip annotations
        }

        void visitType(Type type, JCTree tree) {
            if (type == null) {
                return;
            }
            for(var param : type.getParameterTypes()) {
                visitType(param, tree);
            }
            visitType(type.getReturnType(), tree);
            if (type instanceof Type.ClassType classType) {
                visitReference(classType.tsym, tree);
                for(var argument : classType.getTypeArguments()) {
                    visitType(argument, tree);
                }
            } else if (type instanceof Type.ArrayType arrayType) {
                visitType(arrayType.elemtype, tree);
            }
        }
        
        void visitReference(Symbol symbol, JCTree tree) {
            if (!(symbol instanceof Symbol.MethodSymbol || symbol instanceof Symbol.ClassSymbol)) {
                return;
            }
            
//            if (symbol.isConstructor()) {
//                // Implicit constructors should be ignored
//                return;
//            }
            
            if (symbol.owner.isEnum()) {
                // Do not add enum members
                return;
            }
            
            if (symbol instanceof Symbol.MethodSymbol methodSymbol && isRecordAccessor(methodSymbol)) {
                // records are translated to datatypes, which get implicit deconstructors
                return;
            }

            var enclosingClass = symbol.enclClass();
            if (enclosingClass != null && enclosingClass.fullname.contentEquals("java.lang.String")) {
                // String is handled using custom code
                return;
            }

            if (symbol instanceof Symbol.MethodSymbol && symbol.name.contentEquals("equals")) {
                // equals is in additional.dfy
                return;
            }
            
            if (symbol.owner == symtab.arrayClass) {
                return;
            }
            
            if (symbol.owner.type != Type.noType && symbol.outermostClass().fullname.contentEquals(JVERIFY_CLASS)) {
                return;
            }
            
            if (symbolReferences.containsKey(symbol)) {
                return;
            }
            symbolReferences.put(symbol, new Reference(symbol, compilationUnit, tree));

            switch (symbol) {
                case Symbol.MethodSymbol methodSymbol -> visitType(methodSymbol.type, tree);
                case Symbol.ClassSymbol classSymbol -> {
                    var superSymbol = classSymbol.getSuperclass();
                    visitType(superSymbol, tree);
                    for (var _interface : classSymbol.getInterfaces()) {
                        visitType(_interface, tree);
                    }
                }
                default -> {
                }
            }
        }

        @Override
        public void visitIdent(JCTree.JCIdent tree) {
            visitReference(tree.sym, tree);
            super.visitIdent(tree);
        }

        @Override
        public void visitSelect(JCTree.JCFieldAccess tree) {
            visitReference(tree.sym, tree);
            super.visitSelect(tree);
        }

        @Override
        public void visitNewClass(JCTree.JCNewClass tree) {
            visitReference(tree.constructor, tree);
            super.visitNewClass(tree);
        }
    }
    
    public boolean isRecordAccessor(Symbol.MethodSymbol method) {
        Symbol.ClassSymbol enclosingClass = (Symbol.ClassSymbol) method.owner;

        // First check if the enclosing class is a record
        if ((enclosingClass.flags() & Flags.RECORD) == 0) {
            return false;
        }

        // Record accessor characteristics:
        // 1. Public method
        // 2. No parameters
        // 3. Not static
        // 4. Method name matches a record component name

        if ((method.flags() & Flags.PUBLIC) == 0 ||
                (method.flags() & Flags.STATIC) != 0 ||
                method.params().nonEmpty()) {
            return false;
        }

        // Check if method name matches any record component
        String methodName = method.getSimpleName().toString();

        // Get record components from the class symbol
        // Record components are stored as fields with RECORD flag
        for (Symbol member : enclosingClass.members().getSymbols()) {
            if (member.kind == Kinds.Kind.VAR &&
                    (member.flags() & Flags.RECORD) != 0) {
                if (member.getSimpleName().toString().equals(methodName)) {
                    return true;
                }
            }
        }

        return false;
    }
}
