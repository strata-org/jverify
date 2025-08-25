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

public class NewMissingContractCompiler {

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
    
    public NewMissingContractCompiler(Context context) {
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
                getOrAddType(classSymbol, entry.getValue().compilationUnit);
            } else if (symbol instanceof Symbol.MethodSymbol methodSymbol) {
                var methodDecl = (JCTree.JCMethodDecl)index.getTree(methodSymbol);
                if (methodDecl == null) {
                    var classDecl = getOrAddType(symbol.enclClass(), entry.getValue().compilationUnit);

                    methodDecl = maker.MethodDef(methodSymbol, maker.Block(0, List.nil()));
                    classDecl.defs = classDecl.defs.append(methodDecl);
                } else {
                    methodDecl.body = maker.Block(0, List.nil());
                }
                Symbol.ClassSymbol pureSymbol = elements.getTypeElement(Pure.class.getCanonicalName());
                if (methodSymbol.type.getReturnType().getKind() != TypeKind.VOID) {
                    methodDecl.mods.annotations = methodDecl.mods.annotations.append(
                            maker.Annotation(maker.Ident(pureSymbol), List.nil()));
                }
                methodDecl.mods.annotations = methodDecl.mods.annotations.append(verifyMaker.getVerifyFalseAnnotation());

                Reference reference = entry.getValue();
                reporter.compilationUnit = reference.compilationUnit();
                reporter.reportDiagnostic(reporter.toOrigin(reference.tree), JCDiagnostic.DiagnosticType.WARNING, "missingContract",
                        methodSymbol.getQualifiedName(), symbol.enclClass().getQualifiedName());
            }
        }
    }

    private JCTree.JCClassDecl getOrAddType(Symbol.ClassSymbol classSymbol, JCTree.JCCompilationUnit compilationUnit) {
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
            addType(tree.type, tree);
            super.visitMethodDef(tree);
        }

        @Override
        public void visitAnnotation(JCTree.JCAnnotation tree) {
        }

        void addType(Type type, JCTree tree) {
            if (type == null) {
                return;
            }
            for(var param : type.getParameterTypes()) {
                addType(param, tree);
            }
            addType(type.getReturnType(), tree);
            // trait DeserializationConfig extends MapperConfigBase<com_fasterxml_jackson_databind_DeserializationFeature, DeserializationConfig>
            if (type instanceof Type.ClassType classType) {
                var fresh = !symbolReferences.containsKey(classType.tsym);
                addRef(classType.tsym, tree);
                for(var argument : classType.getTypeArguments()) {
                    addType(argument, tree);
                }
            } else if (type instanceof Type.ArrayType arrayType) {
                addType(arrayType.elemtype, tree);
            }
        }
        
        void addRef(Symbol symbol, JCTree tree) {
            if (!(symbol instanceof Symbol.MethodSymbol || symbol instanceof Symbol.ClassSymbol)) {
                return;
            }
            
            if (symbol.isConstructor()) {
                // Implicit constructors should be ignored
                return;
            }
            
            if (symbol.owner.isEnum()) {
                // Do not add enum members
                return;
            }
            
            if (symbol instanceof Symbol.MethodSymbol methodSymbol && isRecordAccessor(methodSymbol)) {
                return;
            }

            var enclosingClass = symbol.enclClass();
            if (enclosingClass != null && enclosingClass.fullname.contentEquals("java.lang.String")) {
                // String is handled using custom code
                return;
            }
            
            if (symbol.owner == symtab.arrayClass) {
                return;
            }
            
            if (symbol instanceof Symbol.MethodSymbol && symbol.name.contentEquals("equals")) {
                // equals is in additional.dfy
                return;
            }
            
            if (symbol.owner.type != Type.noType && symbol.outermostClass().fullname.contentEquals(JVERIFY_CLASS)) {
                return;
            }
            
            if (symbolReferences.containsKey(symbol)) {
                return;
            }
            symbolReferences.put(symbol, new Reference(symbol, compilationUnit, tree));
                
            if (symbol instanceof Symbol.MethodSymbol methodSymbol) {
                addType(methodSymbol.type, tree);
            }
            
            if (symbol instanceof Symbol.ClassSymbol classSymbol) {
                var superSymbol = classSymbol.getSuperclass();
                addType(superSymbol, tree);
                for(var _interface : classSymbol.getInterfaces()) {
                    addType(_interface, tree);
                }
            }
        }

        @Override
        public void visitIdent(JCTree.JCIdent tree) {
            addRef(tree.sym, tree);
            super.visitIdent(tree);
        }

        @Override
        public void visitSelect(JCTree.JCFieldAccess tree) {
            addRef(tree.sym, tree);
            super.visitSelect(tree);
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
