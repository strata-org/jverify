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
import com.sun.tools.javac.util.*;
import com.sun.tools.javac.util.List;

import javax.lang.model.element.ElementKind;
import javax.lang.model.type.TypeKind;
import java.util.*;

import static com.aws.jverify.verifier.compiler.JavaToDafnyCompiler.JVERIFY_PACKAGE;

public class MissingContractCompiler {

    private final TreeMaker maker;
    private final JVerifyIndex index;
    private final Enter enter;
    private final Names names;
    private final Symtab symtab;
    private final JavacElements elements;
    private final JVerifyMaker jverifyMaker;
    NewMethodOrLoopContractCompiler internalContractCompiler;

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
        jverifyMaker = JVerifyMaker.instance(context);
        reporter = Reporter.instance(context);
        internalContractCompiler = NewMethodOrLoopContractCompiler.instance(context);
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

            Reference reference = entry.getValue();
            var symbol = reference.symbol;
            maker.pos = reference.tree.pos;
            if (symbol instanceof Symbol.ClassSymbol classSymbol) {
                getOrAddType(classSymbol, reference);
            } else if (symbol instanceof Symbol.MethodSymbol methodSymbol) {
                addMissingMethod(reference, methodSymbol);
            } else if (symbol instanceof Symbol.VarSymbol fieldSymbol) {
                if (fieldSymbol.name.contentEquals(names._class)) {
                    // Ignore .class fields for now
                    continue;
                }
                var fieldDecl = (JCTree.JCVariableDecl)index.getTree(fieldSymbol);
                if (fieldDecl == null) {
                    var classDecl = getOrAddType(fieldSymbol.enclClass(), reference);
                    fieldDecl = maker.VarDef(fieldSymbol, null);
                    classDecl.defs = classDecl.defs.append(fieldDecl);
                    reporter.reportDiagnostic(reporter.toOrigin(reference.tree), JCDiagnostic.DiagnosticType.WARNING, "missingContract",
                            fieldSymbol.getQualifiedName(), fieldSymbol.enclClass().getQualifiedName());
                }
            }
        }
    }

    private void addMissingMethod(Reference reference, Symbol.MethodSymbol methodSymbol) {
        var methodDecl = (JCTree.JCMethodDecl)index.getTree(methodSymbol);
        JCTree.JCBlock defaultBody = internalContractCompiler.getDefaultBody();
        if (methodDecl == null) {
            var classDecl = getOrAddType(methodSymbol.enclClass(), reference);
            methodDecl = maker.MethodDef(methodSymbol, defaultBody);
            classDecl.defs = classDecl.defs.append(methodDecl);
        } else {
            methodDecl.body = defaultBody;
        }
        Symbol.ClassSymbol pureSymbol = elements.getTypeElement(Pure.class.getCanonicalName());
        if (methodSymbol.getAnnotation(Pure.class) == null && methodSymbol.type.getReturnType().getKind() != TypeKind.VOID) {
            methodDecl.mods.annotations = methodDecl.mods.annotations.append(
                    maker.Annotation(maker.Ident(pureSymbol), List.nil()));

            ListBuffer<Attribute.Compound> newAnnotations = new ListBuffer<>();
            newAnnotations.addAll(methodSymbol.getAnnotationMirrors());
            newAnnotations.add(new Attribute.Compound(pureSymbol.type, List.nil()));

            methodSymbol.resetAnnotations();
            methodSymbol.setDeclarationAttributes(newAnnotations.toList());
        }
        methodDecl.mods.annotations = methodDecl.mods.annotations.append(jverifyMaker.getVerifyFalseAnnotation());
        jverifyMaker.addVerifyFalseToMethodSymbol(methodSymbol, methodSymbol);

        reporter.compilationUnit = reference.compilationUnit();
        reporter.reportDiagnostic(reporter.toOrigin(reference.tree), JCDiagnostic.DiagnosticType.WARNING, "missingContract",
                methodSymbol.getQualifiedName(), methodSymbol.enclClass().getQualifiedName());
    }

    private JCTree.JCClassDecl getOrAddType(Symbol.ClassSymbol classSymbol, Reference reference) {
        var compilationUnit = reference.compilationUnit();
        if (!foundSymbols.add(classSymbol)) {
            return (JCTree.JCClassDecl) index.getTree(classSymbol);
        }
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
            super.visitMethodDef(tree);
        }

        @Override
        public void visitVarDef(JCTree.JCVariableDecl tree) {
            if (isField(tree.sym)) {
                foundSymbols.add(tree.sym);
            }
            super.visitVarDef(tree);
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
            boolean validSymbol = symbol instanceof Symbol.MethodSymbol || symbol instanceof Symbol.ClassSymbol
                    || (symbol instanceof Symbol.VarSymbol varSymbol && isField(varSymbol));
            if (!validSymbol) {
                return;
            }
            
            if (symbol.isConstructor() && symbol.owner.flatName().contentEquals(Record.class.getCanonicalName())) {
                // Datatype constructors do not call a base constructor
                return;
            }
            
            if (symbol.owner.isEnum()) {
                // Do not add enum members, since enums are translated to datatypes
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
                // Arrays are handled using custom code
                return;
            }
            
            if (isJVerifySymbol(symbol)) {
                // Do not add contracts for JVerify library methods, since they are compiled away anyways
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
                case Symbol.VarSymbol varSymbol -> visitType(varSymbol.type, tree);
                default -> throw new RuntimeException("impossible");
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

    private static boolean isField(Symbol.VarSymbol varSymbol) {
        return varSymbol.getKind() == ElementKind.FIELD && !varSymbol.name.equals(varSymbol.name.table.names._this);
    }

    private static boolean isJVerifySymbol(Symbol symbol) {
        return symbol.owner.type != Type.noType && symbol.outermostClass().packge().fullname.contentEquals(JVERIFY_PACKAGE);
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
