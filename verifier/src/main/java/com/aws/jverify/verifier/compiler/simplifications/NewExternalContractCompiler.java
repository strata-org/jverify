package com.aws.jverify.verifier.compiler.simplifications;

import com.aws.jverify.Contract;
import com.aws.jverify.Verify;
import com.aws.jverify.verifier.compiler.JavaToDafnyCompiler;
import com.aws.jverify.verifier.compiler.OverrideFinder;
import com.aws.jverify.verifier.compiler.frontend.JVerifyIndex;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.Enter;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.model.JavacElements;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Names;

import javax.tools.DiagnosticListener;
import javax.tools.JavaFileObject;
import java.util.*;
import java.util.stream.StreamSupport;

import static com.aws.jverify.verifier.compiler.JavaToDafnyCompiler.isConstructor;

public class NewExternalContractCompiler {
    private final Names names;
    private final JVerifyIndex index;
    private final Enter enter;
    private final Types types;
    private final TreeMaker maker;
    private final Symtab symtab;
    private final DiagnosticListener<JavaFileObject> listener;
    private final JavacElements elements;

    public NewExternalContractCompiler(Context context) {
        this.names = Names.instance(context);
        this.enter = Enter.instance(context);
        this.types = Types.instance(context);
        this.maker = TreeMaker.instance(context);
        this.elements = JavacElements.instance(context);
        this.symtab =  Symtab.instance(context);
        this.index = JVerifyIndex.instance(context);
        this.listener = (DiagnosticListener<JavaFileObject>)context.get(DiagnosticListener.class);
    }
    
    public Collection<JCTree.JCCompilationUnit> apply(Collection<JCTree.JCCompilationUnit> compilationUnits) {
        for(var unit :  compilationUnits) {
            new MoveSourceContracts().visitTopLevel(unit);
        }
        return compilationUnits;
    }

    class MoveSourceContracts extends TreeTranslator {

        Set<JCTree.JCClassDecl> classesToRemove = new HashSet<>();

        private Env<AttrContext> topLevelEnv;
        
        @Override
        public void visitTopLevel(JCTree.JCCompilationUnit tree) {
            topLevelEnv = enter.getTopLevelEnv(tree);
            super.visitTopLevel(tree);
        }

        @Override
        public void visitClassDef(JCTree.JCClassDecl classDecl) {
            var classAnnotationsByName = JavaToDafnyCompiler.getAnnotationsByName(classDecl.getModifiers());
            var contractAnnotation = classAnnotationsByName.get(Contract.class.getName());
            if (contractAnnotation != null) {
                var contracteeSymbol = getContractTarget(classDecl, contractAnnotation);
                var contracteeSource = (JCTree.JCClassDecl)index.getTree(contracteeSymbol);
                if (contracteeSource == null) {
                    handleLibraryContract(classDecl, contracteeSymbol);
                } else {
                    classesToRemove.add(classDecl);
                    // TODO see if I can move the external contract into the source class
                    // We'll have to add a body to bodyless members
                    for(var member : classDecl.getMembers()) {
                        if (member instanceof JCTree.JCVariableDecl field) {
                            contracteeSource.defs = contracteeSource.defs.append(field);
                            contracteeSource.sym.members().enter(field.sym);
                        } else if (member instanceof JCTree.JCMethodDecl methodDecl) {
                            var methodSymbol = methodDecl.sym;
                            if (JavaToDafnyCompiler.isSynthetic(index, methodDecl, methodSymbol) || (methodDecl.mods.flags & Flags.GENERATEDCONSTR) != 0) {
                                continue;
                            }
                            var baseMethod = OverrideFinder.findOverriddenMethod(contracteeSymbol, methodSymbol, types);
                            if (baseMethod != null) {
                                var baseSource = (JCTree.JCMethodDecl)index.getTree(baseMethod);
                                if (baseSource.getBody() != null) {
                                    throw new RuntimeException("not allowed");
                                } else {
                                    baseSource.body = methodDecl.body;
                                }
                            } else {
                                throw new RuntimeException();
                            }
                        }
                    }
                }
            }
            
            super.visitClassDef(classDecl);
        }

        private void handleLibraryContract(JCTree.JCClassDecl classDecl, Symbol.ClassSymbol contracteeSymbol) {
            if (contracteeSymbol == symtab.objectType.tsym) {
                return;
            }
            var oldSymbol = classDecl.sym;
            classDecl.type.tsym = contracteeSymbol;
            classDecl.sym = contracteeSymbol;
            classDecl.name = classDecl.sym.name;

            var verifySymbol = elements.getTypeElement(Verify.class.getCanonicalName());
            classDecl.mods.annotations = classDecl.mods.annotations.append(maker.Annotation(maker.Ident(verifySymbol), List.of(
                    maker.Assign(maker.Ident(names.fromString("value")), maker.Literal(false)))));
            
            // TODO move annotations to contracteeSymbol
            var x = oldSymbol.getDeclarationAttributes();
            var b = 3;
            
            // TODO traverse classDecl and update references from old sym to new sym

            index.put(classDecl.sym, enter.classEnv(classDecl, topLevelEnv));
            for(var member : classDecl.getMembers()) {
                if (member instanceof JCTree.JCMethodDecl methodDecl) {
                    var methodSymbol = methodDecl.sym;
                    var baseMethod = OverrideFinder.findOverriddenMethod(contracteeSymbol, methodSymbol, types);
                    if (baseMethod != null) {
                        // If we update the sym, then we need to be careful with parameter names
                        // methodDecl.sym = baseMethod;
                    } else {
                        // Check currently does not take into account overloading
                        // But this only makes it not detect some unused methods.
                        var contractee = StreamSupport.stream(contracteeSymbol.members().getSymbolsByName(methodSymbol.name).spliterator(), false).toList();
                        if (contractee.isEmpty()) {
                            reportError(methodDecl, "unusedContractMethod", methodToString(methodDecl));
                        }
                        // TODO shouldn't we always throw an error here?
                        // TODO remove the method ?
                    }
                }
            }
        }

        public Symbol.ClassSymbol getContractTarget(JCTree.JCClassDecl classDecl,
                                                    JCTree.JCAnnotation contractAnnotation) {
            if (contractAnnotation == null) {
                return null;
            }

            var arguments = JavaToDafnyCompiler.getArguments(contractAnnotation);
            var symbol = JavaToDafnyCompiler.getClassSymbol(names,  arguments.get("value"));
            if (symbol == null || symbol.getQualifiedName().contentEquals("com.aws.jverify.Contract")) {
                var superClass = classDecl.sym.getSuperclass();
                if (classDecl.extending != null && superClass != null) {
                    return (Symbol.ClassSymbol) superClass.tsym;
                }
                var interfaces = classDecl.sym.getInterfaces();
                if (interfaces.isEmpty()) {
                    return null;
                }
                return (Symbol.ClassSymbol) interfaces.getFirst().tsym;
            }
            return symbol;
        }
    }

    private void reportError(JCTree.JCMethodDecl methodDecl, String key, String... arguments) {
        
    }

    private String methodToString(JCTree tree) {
        if (tree instanceof JCTree.JCMethodDecl methodDecl){
            if (isConstructor(methodDecl.sym)) {
                return "constructor";
            } else {
                return "method '" + methodDecl.name + "'";
            }
        } else {
            return "lambda";
        }
    }


    private ExternalTypeContract getExternalTypeContract(JCTree.JCClassDecl classDecl, Symbol.ClassSymbol contracteeSymbol) {
        Map<Symbol.MethodSymbol, JCTree.JCMethodDecl> externalContracts = new HashMap<>();
        var ghostFields = new ArrayList<JCTree.JCVariableDecl>();
        for(var member : classDecl.getMembers()) {

            if (member instanceof JCTree.JCVariableDecl field) {
                ghostFields.add(field);
            }

            if (!(member instanceof JCTree.JCMethodDecl methodDecl)) {
                continue;
            }
            var methodSymbol = methodDecl.sym;
            if (JavaToDafnyCompiler.isSynthetic(index, methodDecl, methodSymbol) || (methodDecl.mods.flags & Flags.GENERATEDCONSTR) != 0) {
                continue;
            }
            var baseMethod = OverrideFinder.findOverriddenMethod(contracteeSymbol, methodSymbol, types);
            if (baseMethod != null) {
                externalContracts.put(baseMethod, methodDecl);
            } else {
                // Check currently does not take into account overloading
                // But this only makes it not detect some unused methods.
                var contractee = StreamSupport.stream(contracteeSymbol.members().getSymbolsByName(methodSymbol.name).spliterator(), false).toList();
                if (contractee.isEmpty()) {
                    reportError(methodDecl, "unusedContractMethod", methodToString(methodDecl));
                }
            }
        }
        return new ExternalTypeContract(externalContracts, ghostFields);
    }
    
}
