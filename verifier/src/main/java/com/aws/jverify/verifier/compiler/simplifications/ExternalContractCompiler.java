package com.aws.jverify.verifier.compiler.simplifications;

import com.aws.jverify.Contract;
import com.aws.jverify.verifier.compiler.BlockCompiler;
import com.aws.jverify.verifier.compiler.frontend.JVerifyIndex;
import com.aws.jverify.verifier.compiler.JavaToDafnyCompiler;
import com.aws.jverify.verifier.compiler.MethodOrLoopContract;
import com.aws.jverify.verifier.compiler.OverrideFinder;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.tree.TreeScanner;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.tree.JCTree;

import java.util.*;
import java.util.stream.StreamSupport;

import static com.aws.jverify.verifier.compiler.JavaToDafnyCompiler.isConstructor;

public class ExternalContractCompiler extends TreeScanner {
    final JavaToDafnyCompiler compiler;
    public final Map<Symbol.ClassSymbol, List<JCTree.JCClassDecl>> declarationsForSymbolContract = new HashMap<>();
    public final Map<Symbol.ClassSymbol, ExternalTypeContract> externalContracts = new HashMap<>();
    public Map<com.sun.tools.javac.code.Type, com.sun.tools.javac.code.Type> contractClassTypeToContracteeType = new HashMap<>();
    public LinkedHashMap<Symbol.ClassSymbol, Symbol.ClassSymbol> contractClassToContractee = new LinkedHashMap<>();
    public Map<Symbol.ClassSymbol, JCTree.JCCompilationUnit> foundClasses = new HashMap<>();
    
    public ExternalContractCompiler(JavaToDafnyCompiler compiler) {
        this.compiler = compiler;
    }
    
    public record ExternalTypeContract(
            Map<Symbol.MethodSymbol, MethodOrLoopContract> methodContracts,
            List<JCTree.JCVariableDecl> ghostFields) { }


    @Override
    public void visitClassDef(JCTree.JCClassDecl classDecl) {
        super.visitClassDef(classDecl);

        var classAnnotationsByName = JavaToDafnyCompiler.getAnnotationsByName(classDecl.getModifiers());
        var contractAnnotation = classAnnotationsByName.get(Contract.class.getName());

        if (contractAnnotation == null) {
            var declsForSymbol = declarationsForSymbolContract.computeIfAbsent(classDecl.sym, (_) -> new ArrayList<>());
            declsForSymbol.add(classDecl);
            foundClasses.put(classDecl.sym, compiler.compilationUnit);
            return;
        }

        var contracteeSymbol = getContractTarget(classDecl, contractAnnotation);
        if (contracteeSymbol == null) {
            compiler.reportError(classDecl, "noContractTarget", classDecl.name.toString());
            return;
        }

        com.sun.tools.javac.util.List<Symbol.TypeVariableSymbol> typeParameters = contracteeSymbol.getTypeParameters();
        if (typeParameters.size() != classDecl.sym.getTypeParameters().size()) {
            compiler.reportError(classDecl, "contractDifferentTypeParameters", classDecl.name.toString(), contracteeSymbol.name.toString());
            return;
        }
        
        for (int i = 0; i < typeParameters.size(); i++) {
            var originalTypeParameter = typeParameters.get(i);
            var contractTypeParameter = classDecl.sym.getTypeParameters().get(i);
            if (!originalTypeParameter.name.contentEquals(contractTypeParameter.name)) {
                compiler.reportError(classDecl, "contractDifferentTypeParameters", classDecl.name.toString(), contracteeSymbol.name.toString());
                return;
            }
        }

        var declsForSymbol = declarationsForSymbolContract.computeIfAbsent(contracteeSymbol, (_) -> new ArrayList<>());
        declsForSymbol.add(classDecl);

        foundClasses.put(contracteeSymbol, compiler.compilationUnit);
        if (compiler.typeHasSource(contracteeSymbol) && !JavaToDafnyCompiler.isInterfaceOrAbstract(contracteeSymbol)) {
            compiler.reportError(contractAnnotation, "concreteTypeWithExternalContract", contracteeSymbol.name);
            return;
        }

        this.contractClassToContractee.put(classDecl.sym, contracteeSymbol);
        this.contractClassTypeToContracteeType.put(classDecl.sym.type, contracteeSymbol.type);
    }

    public void discoverTypesAndContractClasses(JCTree.JCCompilationUnit compilationUnit) {
        compiler.compilationUnit = compilationUnit;
        this.visitTopLevel(compilationUnit);
    }

    public void registerExternalContracts() {
        for(var entry : contractClassToContractee.entrySet()) {
            var externalContractDecl = (JCTree.JCClassDecl) JVerifyIndex.instance(compiler.context).getTree(entry.getKey());

            JVerifyIndex index = JVerifyIndex.instance(compiler.context);
            Env<AttrContext> env = index.getEnv(externalContractDecl.sym);
            if (env != null) {
                compiler.compilationUnit = env.toplevel;
            }
            Symbol.ClassSymbol contractee = entry.getValue();
            var externalContract = getExternalTypeContract(externalContractDecl, contractee);
            if (externalContracts.containsKey(contractee)) {
                var annotations = JavaToDafnyCompiler.getAnnotationsByName(externalContractDecl.getModifiers());
                var contractAnnotation = annotations.get(Contract.class.getName());
                compiler.reportError(contractAnnotation, "duplicateContract", contractee.name);
                continue;
            }
            this.externalContracts.put(contractee, externalContract);
        }
    }

    private ExternalTypeContract getExternalTypeContract(JCTree.JCClassDecl classDecl, Symbol.ClassSymbol contracteeSymbol) {
        Map<Symbol.MethodSymbol, MethodOrLoopContract> externalContracts = new HashMap<>();
        List<JCTree.JCVariableDecl> ghostFields = new ArrayList<>();
        for(var member : classDecl.getMembers()) {

            if (member instanceof JCTree.JCVariableDecl field) {
                ghostFields.add(field);
            }
            
            if (!(member instanceof JCTree.JCMethodDecl methodDecl)) {
                continue;
            }
            var methodSymbol = methodDecl.sym;
            if (compiler.isSynthetic(methodDecl, methodSymbol) || (methodDecl.mods.flags & Flags.GENERATEDCONSTR) != 0) {
                continue;
            }
            var baseMethod = OverrideFinder.findOverriddenMethod(contracteeSymbol, methodSymbol, Types.instance(compiler.context));
            if (baseMethod != null) {
                BlockCompiler blockCompiler = new BlockCompiler(compiler, methodSymbol);
                var header = blockCompiler.extractContract(methodDecl, true);
                var baseMethodDecl = (JCTree.JCMethodDecl) JVerifyIndex.instance(compiler.context).getTree(baseMethod);
                if (baseMethodDecl != null) {
                    var baseHeader= blockCompiler.extractContract(baseMethodDecl, true);
                    if (baseHeader.isPure) {
                        compiler.reportError(baseMethodDecl, "pureOnContractedMethod", baseMethod.name.toString());
                    }
                } 
                externalContracts.put(baseMethod, header);
            } else {
                // Check currently does not take into account overloading
                // But this only makes it not detect some unused methods.
                var contractee = StreamSupport.stream(contracteeSymbol.members().getSymbolsByName(methodSymbol.name).spliterator(), false).toList();
                if (contractee.isEmpty()) {
                    compiler.reportError(methodDecl, "unusedContractMethod", methodToString(methodDecl));
                }
            }
        }
        return new ExternalTypeContract(externalContracts, ghostFields);
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
    
    public Symbol.ClassSymbol getContractTarget(JCTree.JCClassDecl classDecl,
                                                JCTree.JCAnnotation contractAnnotation) {
        if (contractAnnotation == null) {
            return null;
        }

        var arguments = JavaToDafnyCompiler.getArguments(contractAnnotation);
        var symbol = compiler.getClassSymbol(arguments.get("value"));
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
