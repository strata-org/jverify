package com.aws.jverify.verifier.compiler.transformations;

import com.aws.jverify.Contract;
import com.aws.jverify.verifier.compiler.BlockCompiler;
import com.aws.jverify.verifier.compiler.JavaToDafnyCompiler;
import com.aws.jverify.verifier.compiler.MethodOrLoopContract;
import com.aws.jverify.verifier.compiler.OverrideFinder;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.tree.JCTree;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static com.aws.jverify.verifier.compiler.JavaToDafnyCompiler.isConstructor;

public class ExternalContractCompiler {
    JavaToDafnyCompiler compiler;
    public final Map<Symbol.ClassSymbol, List<JCTree.JCClassDecl>> declarationsForSymbolContract = new HashMap<>();

    public ExternalContractCompiler(JavaToDafnyCompiler compiler) {
        this.compiler = compiler;
    }

    public final Map<Symbol.ClassSymbol, ExternalTypeContract> externalContracts = new HashMap<>();
    
    public record ExternalTypeContract(Map<Symbol.MethodSymbol, MethodOrLoopContract> methodContracts) { }
    
    public void discoverContracts(JCTree.JCCompilationUnit compilationUnit, Set<Symbol.ClassSymbol> foundSymbols) {
        compiler.compilationUnit = compilationUnit;
        
        var typesToVisit = new LinkedList<>(compilationUnit.getTypeDecls());
        while(!typesToVisit.isEmpty()) {
            var typeDecl = typesToVisit.poll();
            if (!(typeDecl instanceof JCTree.JCClassDecl classDecl)) {
                continue;
            }

            var classAnnotations = classDecl.getModifiers().getAnnotations();
            var classAnnotationsByName = classAnnotations.stream().collect(Collectors.toMap(
                    (JCTree.JCAnnotation a) -> a.getAnnotationType().type.toString(),
                    a -> a));
            var contractAnnotation = classAnnotationsByName.get(Contract.class.getName());

            for(var member : classDecl.getMembers()) {
                if (member instanceof JCTree.JCClassDecl nestedClass) {
                    typesToVisit.push(nestedClass);
                }
            }
            if (contractAnnotation == null) {
                var declsForSymbol = declarationsForSymbolContract.computeIfAbsent(classDecl.sym, (_) -> new ArrayList<>());
                declsForSymbol.add(classDecl);
                foundSymbols.add(classDecl.sym);
                continue;
            }

            var contracteeSymbol = getContractTarget(classDecl, contractAnnotation);
            if (contracteeSymbol == null) {
                compiler.reportError(classDecl, "noContractTarget", classDecl.name.toString());
                continue;
            }

            var declsForSymbol = declarationsForSymbolContract.computeIfAbsent(contracteeSymbol, (_) -> new ArrayList<>());
            declsForSymbol.add(classDecl);

            foundSymbols.add(contracteeSymbol);
            if (externalContracts.containsKey(contracteeSymbol)) {
                compiler.reportError(contractAnnotation, "duplicateContract", contracteeSymbol.name);
                continue;
            }
            if (compiler.typeHasSource(contracteeSymbol) && !JavaToDafnyCompiler.isInterfaceOrAbstract(contracteeSymbol)) {
                compiler.reportError(contractAnnotation, "concreteTypeWithExternalContract", contracteeSymbol.name);
                continue;
            }

            Map<Symbol.MethodSymbol, MethodOrLoopContract> externalContracts = new HashMap<>();
            for(var member : classDecl.getMembers()) {
                if (member instanceof JCTree.JCClassDecl nestedClass) {
                    typesToVisit.push(nestedClass);
                }

                if (!(member instanceof JCTree.JCMethodDecl methodDecl)) {
                    continue;
                }

                var methodSymbol = methodDecl.sym;
                var baseMethod = OverrideFinder.findOverriddenMethod(methodSymbol, Types.instance(compiler.context));
                if (baseMethod != null) {
                    var header = new BlockCompiler(compiler).extractContract(methodDecl, true);
                    externalContracts.put(baseMethod, header);
                    compiler.methodContracts.put(baseMethod, header);
                } else if (!compiler.isSynthetic(methodDecl, methodSymbol)) {
                    // Check currently does not take into account overloading
                    // But this only makes it not detect some unused methods.
                    var contractee = StreamSupport.stream(contracteeSymbol.members().getSymbolsByName(methodSymbol.name).spliterator(), false).toList();
                    if (contractee.isEmpty()) {
                        compiler.reportError(methodDecl, "unusedContractMethod", methodToString(methodDecl));
                    }
                }
            }
            this.externalContracts.put(contracteeSymbol, new ExternalTypeContract(externalContracts));
        }
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
    
    public static Symbol.ClassSymbol getContractTarget(JCTree.JCClassDecl classDecl,
                                                JCTree.JCAnnotation contractAnnotation) {
        if (contractAnnotation == null) {
            return null;
        }

        var arguments = JavaToDafnyCompiler.getArguments(contractAnnotation);
        var symbol = JavaToDafnyCompiler.getClassSymbol(arguments.get("value"));
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
