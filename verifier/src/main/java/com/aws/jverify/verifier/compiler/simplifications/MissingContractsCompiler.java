package com.aws.jverify.verifier.compiler.simplifications;

import com.aws.jverify.generated.*;
import com.aws.jverify.verifier.compiler.JavaToDafnyCompiler;
import com.aws.jverify.verifier.compiler.Reporter;
import com.aws.jverify.verifier.compiler.TypeDeclarationCompiler;
import com.aws.jverify.verifier.compiler.frontend.JVerifyIndex;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.JCDiagnostic;

import java.util.*;
import java.util.concurrent.Flow;

/**
 * Add empty contracts for types and type members that are referenced from sources but that don't have a contract.
 */
public class MissingContractsCompiler {
    JavaToDafnyCompiler compiler;
    TypeDeclarationCompiler  typeDeclarationCompiler;
    
    record MissingContract(Symbol symbol, JCTree.JCCompilationUnit compilationUnit, IOrigin origin) {}
    
    private final Queue<MissingContract> missingContracts = new LinkedList<>();
    private final Reporter reporter;

    public MissingContractsCompiler(JavaToDafnyCompiler compiler) {
        this.compiler = compiler;
        reporter = Reporter.instance(compiler.context);
        typeDeclarationCompiler = compiler.typeDeclarationCompiler;

        compiler.nameCompiler.foundSymbols().subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                // Never emitted
            }

            @Override
            public void onNext(NameCompiler.FoundSymbol foundSymbol) {
                var symbol = foundSymbol.symbol();
                if (typeDeclarationCompiler.createdContracts.contains(symbol)) {
                    return;
                }
                if (symbol instanceof Symbol.ClassSymbol || symbol instanceof Symbol.MethodSymbol) {
                    missingContracts.add(new MissingContract(symbol, 
                            reporter.compilationUnit, foundSymbol.origin()));
                }
            }

            @Override
            public void onError(Throwable throwable) {
                // Never emitted
            }

            @Override
            public void onComplete() {
                // Never emitted
            }
        });
    }

    public void addMissingTypeContracts(List<FileHeader> filesStarts) {
        Map<String, TopLevelDeclWithMembers> topLevelDecls = new HashMap<>();
        for(var fileHeader : filesStarts) {
            for(var topLevelDecl : fileHeader.getTopLevelDecls()) {
                topLevelDecls.put(topLevelDecl.getNameNode().getValue(), (TopLevelDeclWithMembers)topLevelDecl);
            }
        }

        while(!missingContracts.isEmpty()) {
            // We need to loop,
            // because inheritance can mean that adding missing contracts introduces new missing contracts

            var missingContract = missingContracts.poll();
            var symbol = missingContract.symbol();
            if (!typeDeclarationCompiler.createdContracts.add(symbol)) {
                continue;
            }
            
            if (symbol instanceof Symbol.ClassSymbol) {
                addMissingType(filesStarts, missingContract, topLevelDecls);
            } else if (symbol instanceof Symbol.MethodSymbol methodSymbol) {
                addMissingMethod(methodSymbol, topLevelDecls, missingContract);
            }
        }
    }

    private void addMissingMethod(Symbol.MethodSymbol methodSymbol,
                                  Map<String, TopLevelDeclWithMembers> topLevelDecls, 
                                  MissingContract missingContract) {
        var clazz = (Symbol.ClassSymbol) methodSymbol.getEnclosingElement();
        String clazzName = compiler.nameCompiler.getCompiledName(clazz, missingContract.origin());
        var clazzDecl = topLevelDecls.get(clazzName);
        if (clazzDecl == null) {
            // wait for missing class to be added
            missingContracts.add(missingContract);
            return;
        }
        var name = compiler.nameCompiler.getCompiledName(methodSymbol, missingContract.origin());
        var typeParameters = typeDeclarationCompiler.translateTypeParameters(missingContract.origin(), methodSymbol.getTypeParameters());
        com.sun.tools.javac.code.Type returnType = methodSymbol.getReturnType();
        MethodOrFunction callable;
        if (returnType.isPrimitiveOrVoid() && !returnType.isPrimitive()) {
            callable = new Method(missingContract.origin(), new Name(missingContract.origin(), name), null, false, null,
                    typeParameters, getIns(methodSymbol, missingContract.origin()),
                    List.of(),
                    List.of(), new Specification<>(List.of(), null), new Specification<>(List.of(), null),
                    new Specification<>(List.of(), null),
                    methodSymbol.isStatic(), List.of(), null,false);
        } else {
            callable = new Function(missingContract.origin(), new Name(missingContract.origin(), name), null, false, null,
                    typeParameters, getIns(methodSymbol, missingContract.origin()),
                    List.of(), List.of(), new Specification<>(List.of(), null), new Specification<>(List.of(), null),
                    methodSymbol.isStatic(), false, null, compiler.translateType(returnType, missingContract.origin()),
                    null, null, null);
        }
        reporter.compilationUnit = missingContract.compilationUnit();
        reporter.reportDiagnostic(reporter.positionFromOrigin(missingContract.origin()), JCDiagnostic.DiagnosticType.WARNING, "missingContract",
                methodSymbol.getQualifiedName(), reporter.getOriginal(clazz).getQualifiedName());
        clazzDecl.getMembers().add(callable);
    }

    private void addMissingType(List<FileHeader> filesStarts, 
                                MissingContract missingContract, Map<String, TopLevelDeclWithMembers> topLevelDecls) {
        var classSymbol = (Symbol.ClassSymbol) missingContract.symbol;
        compiler.nameCompiler.registerClassSymbol(classSymbol);
        String compiledName = compiler.nameCompiler.getCompiledName(classSymbol, missingContract.origin());
        if (compiledName.isEmpty()) {
            // Defensive programming. Some types like intersection types have no name, although they should not occur here
            return;
        }
        
        List<TypeParameter> typeParameters = typeDeclarationCompiler.translateTypeParameters(missingContract.origin(), classSymbol.getTypeParameters());
        TraitDecl trait = typeDeclarationCompiler.getTraitDecl(
                missingContract.origin(),
                new Name(missingContract.origin(), compiledName),
                classSymbol,
                typeParameters, new ArrayList<>());

        topLevelDecls.put(compiledName, trait);
        filesStarts.getFirst().getTopLevelDecls().add(trait);
    }

    private List<Formal> getIns(Symbol.MethodSymbol methodSymbol, IOrigin bodyOrigin) {
        return methodSymbol.extraParams.
                appendList(methodSymbol.getParameters()).
                appendList(methodSymbol.capturedLocals).map(jvd -> {
                    var index = JVerifyIndex.instance(compiler.context);
                    var parameter = index.getTree(jvd);
                    IOrigin parameterOrigin;
                    if (parameter == null) {
                        parameterOrigin = bodyOrigin;
                    } else {
                        parameterOrigin = compiler.toOrigin(parameter);
                    }
                    Name formalName = new Name(parameterOrigin, compiler.nameCompiler.getCompiledName(jvd, parameterOrigin));
                    var syntacticType = compiler.translateMethodSignatureType(jvd.type, parameterOrigin, false);
                    return new Formal(parameterOrigin, formalName, syntacticType, false, true,
                            null, null, false, false, false, null);
                });
    }
}
