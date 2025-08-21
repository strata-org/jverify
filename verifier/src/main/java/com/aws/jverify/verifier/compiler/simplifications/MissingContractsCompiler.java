package com.aws.jverify.verifier.compiler.simplifications;

import com.aws.jverify.generated.*;
import com.aws.jverify.verifier.compiler.JavaToDafnyCompiler;
import com.aws.jverify.verifier.compiler.TypeDeclarationCompiler;
import com.aws.jverify.verifier.compiler.frontend.JVerifyIndex;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.util.JCDiagnostic;

import java.util.*;
import java.util.concurrent.Flow;

public class MissingContractsCompiler {
    JavaToDafnyCompiler compiler;
    TypeDeclarationCompiler  typeDeclarationCompiler;
    private final Map<Symbol, IOrigin> missingContracts = new HashMap<>();

    public MissingContractsCompiler(JavaToDafnyCompiler compiler) {
        this.compiler = compiler;
        typeDeclarationCompiler = compiler.typeDeclarationCompiler;

        compiler.nameCompiler.foundSymbols().subscribe(new Flow.Subscriber<NameCompiler.FoundSymbol>() {
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
                    missingContracts.put(symbol, foundSymbol.origin());
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
        var dummyToken = new Token(1, 1);
        IOrigin dummyOrigin = new TokenRangeOrigin(dummyToken, dummyToken);

        Map<String, TopLevelDeclWithMembers> topLevelDecls = new HashMap<>();
        for(var fileHeader : filesStarts) {
            for(var topLevelDecl : fileHeader.getTopLevelDecls()) {
                topLevelDecls.put(topLevelDecl.getNameNode().getValue(), (TopLevelDeclWithMembers)topLevelDecl);
            }
        }
        var missingNonTypes = new HashMap<Symbol, IOrigin>();
        for(var entry : missingContracts.entrySet()) {
            if (!(entry.getKey() instanceof Symbol.ClassSymbol)) {
                missingNonTypes.put(entry.getKey(), entry.getValue());
            }
        }
        for(var nonType : missingNonTypes.keySet()) {
            missingContracts.remove(nonType);
        }

        while(!missingContracts.isEmpty()) {
            // Because inheritance can mean that adding missing contracts introduces new missing contracts
            // We need to loop

            var entry = missingContracts.entrySet().iterator().next();
            var symbol = entry.getKey();
            missingContracts.remove(symbol);
            if (!typeDeclarationCompiler.createdContracts.add(symbol)) {
                continue;
            }
            String compiledName = compiler.nameCompiler.getCompiledName(symbol, entry.getValue());
            if (compiledName.isEmpty()) {
                // Defensive programming. Some types like intersection types have no name, although they should not occur here
                continue;
            }
            if (symbol instanceof Symbol.ClassSymbol classSymbol) {
                List<TypeParameter> typeParameters = typeDeclarationCompiler.translateTypeParameters(dummyOrigin, symbol.getTypeParameters());
                TraitDecl trait = typeDeclarationCompiler.getTraitDecl(
                        dummyOrigin,
                        new Name(dummyOrigin, compiledName),
                        classSymbol,
                        typeParameters, new ArrayList<>());

                topLevelDecls.put(compiledName, trait);
                filesStarts.getFirst().getTopLevelDecls().add(trait);
            }
        }

        while(!missingNonTypes.isEmpty()) {
            // TODO change while into enhanced for

            var entry = missingNonTypes.entrySet().iterator().next();
            var symbol = entry.getKey();
            var origin = entry.getValue();
            missingNonTypes.remove(symbol);
            if (!typeDeclarationCompiler.createdContracts.add(symbol)) {
                continue;
            }
            if (symbol instanceof Symbol.MethodSymbol methodSymbol) {
                var clazz = (Symbol.ClassSymbol)methodSymbol.getEnclosingElement();
                String clazzName = compiler.nameCompiler.getCompiledName(clazz, origin);
                var clazzDecl = topLevelDecls.get(clazzName);
                var name = compiler.nameCompiler.getCompiledName(methodSymbol, origin);
                var typeParameters = typeDeclarationCompiler.translateTypeParameters(dummyOrigin, methodSymbol.getTypeParameters());
                com.sun.tools.javac.code.Type returnType = methodSymbol.getReturnType();
                MethodOrFunction callable;
                if (returnType.isPrimitiveOrVoid() && !returnType.isPrimitive()) {
                    callable = new Method(dummyOrigin, new Name(dummyOrigin, name), null, false, null,
                            typeParameters, getIns(methodSymbol, dummyOrigin),
                            List.of(new AttributedExpression(new LiteralExpr(dummyOrigin, false), null, null)),
                            List.of(), new Specification<>(List.of(), null), new Specification<>(List.of(), null),
                            new Specification<>(List.of(), null),
                            methodSymbol.isStatic(), List.of(), null,false);
                } else {
                    callable = new Function(dummyOrigin, new Name(dummyOrigin, name), null, false, null,
                            typeParameters, getIns(methodSymbol, dummyOrigin),
                            List.of(), List.of(), new Specification<>(List.of(), null), new Specification<>(List.of(), null),
                            methodSymbol.isStatic(), false, null, compiler.translateType(returnType, dummyOrigin),
                            null, null, null);
                }
                compiler.reporter.reportDiagnostic(origin, JCDiagnostic.DiagnosticType.WARNING, "missingContract",
                        methodSymbol.getQualifiedName(), clazz.getQualifiedName());
                clazzDecl.getMembers().add(callable);
            }
        }
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
