package com.aws.jverify.verifier.compiler.simplifications.workaround;

import com.aws.jverify.Nullable;
import com.aws.jverify.generated.*;
import com.aws.jverify.verifier.compiler.TypeDeclarationCompiler;
import com.aws.jverify.verifier.compiler.JavaToDafnyCompiler;
import com.aws.jverify.verifier.compiler.simplifications.NameCompiler;
import com.aws.jverify.verifier.compiler.simplifications.VerifyAnnotationCompiler;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.model.JavacElements;
import com.sun.tools.javac.tree.JCTree;

import java.util.*;

/**
 * Since Dafny does not support traits with constructors,
 * this compiler splits such a trait into a trait and a class, moving the constructor to the class
 */
public class TraitWithConstructorCompiler {
    private final TypeDeclarationCompiler typeDeclarationCompiler;
    private final VerifyAnnotationCompiler verifyAnnotationCompiler;
    private final Types types;
    private final NameCompiler nameCompiler;

    public TraitWithConstructorCompiler(TypeDeclarationCompiler typeDeclarationCompiler) {
        this.typeDeclarationCompiler = typeDeclarationCompiler;
        types = Types.instance(typeDeclarationCompiler.compiler.context);
        verifyAnnotationCompiler = new VerifyAnnotationCompiler(typeDeclarationCompiler.compiler.context);
        nameCompiler = typeDeclarationCompiler.compiler.nameCompiler;
    }
    
    private Map<Symbol.TypeSymbol, Set<MethodOrFunction>> inheritedAssumedMethodsForTypes = new HashMap<>();
    public Set<MethodOrFunction> getAssumedMethods(Symbol.TypeSymbol typeSymbol, IOrigin origin) {
        var result = inheritedAssumedMethodsForTypes.get(typeSymbol);
        if (result == null) {
            result = new HashSet<>();
            var names = new HashSet<String>();
            for(var member : typeSymbol.members().getSymbols()) {
                if (member instanceof Symbol.MethodSymbol methodSymbol) {
                    if (verifyAnnotationCompiler.removedImplementations.contains(methodSymbol)) {
                        MethodOrFunction callable = typeDeclarationCompiler.callables.get(methodSymbol);
                        if (callable != null &&
                                (callable instanceof Method method && method.getBody() == null ||
                                        callable instanceof Function function && function.getBody() == null)) {
                            result.add(callable);
                        }
                    } else {
                        names.add(nameCompiler.getCompiledName(methodSymbol, origin));
                    }
                }
            }
            for(var baseType : types.closure(typeSymbol.type)) {
                if (baseType.tsym != typeSymbol) {
                    for(var assumed : getAssumedMethods(baseType.tsym, origin)) {
                        if (!names.contains(assumed.getNameNode().getValue())) {
                            result.add(assumed);
                        }
                    }
                }
            }
            inheritedAssumedMethodsForTypes.put(typeSymbol, result);
        }
        return result;
    }

    public @Nullable List<TopLevelDecl> compile(TopLevelDecl clazz, Symbol.ClassSymbol symbol) {
        if (clazz instanceof TraitDecl traitDecl) {
            return buildTraitAndClassTwin(traitDecl, symbol);
        }
        return List.of(clazz);
    }
    
    /**
     * Translating Java classes to both a Dafny trait and a class is used to support classes extending classes
     */
    private List<TopLevelDecl> buildTraitAndClassTwin(TraitDecl traitDecl, 
                                                      Symbol.ClassSymbol classSymbol) {
        var traitMembers = new ArrayList<MemberDecl>();
        var classMembers = new ArrayList<MemberDecl>();
        var classNeeded = false;

        for(var member : traitDecl.getMembers()) {
            switch (member) {
                case Method method when !method.getHasStaticKeyword() -> {
                    traitMembers.add(member);
                }

                case Function function -> {
                    traitMembers.add(function);
                }
                case Constructor constructor -> {
                    classNeeded = true;
                    traitMembers.add(constructorToInitMethod(traitDecl.getNameNode().getValue(), constructor));

                    var classConstructor = new Constructor(constructor.getOrigin(), constructor.getNameNode(), null, false, null,
                            constructor.getTypeArgs(), constructor.getIns(),
                            constructor.getReq(), constructor.getEns(), constructor.getReads(),
                            constructor.getDecreases(), constructor.getMod(),
                            null);
                    classMembers.add(classConstructor);
                }
                default -> traitMembers.add(member);
            }
        }

        if (classNeeded) {
            // this equals function is assumed but declared in additional.dfy, so it's not detected at the Java level.
            classMembers.add(JavaToDafnyCompiler.equalsFunctionDeclaration(traitDecl.getOrigin()));
            classMembers.addAll(getAssumedMethods(classSymbol, traitDecl.getOrigin()));

            List<TypeParameter> typeParameters = traitDecl.getTypeArgs();
            Name nameNode = traitDecl.getNameNode();
            var trait = new TraitDecl(traitDecl.getOrigin(), nameNode, traitDecl.getAttributes(),
                    typeParameters, traitMembers, traitDecl.getTraits(), false);

            var typeArgs = typeParameters.stream().map(
                    p -> (Type)new UserDefinedType(p.getOrigin(),
                            new NameSegment(p.getOrigin(), p.getNameNode().getValue(), null))).toList();
            var traitRef = new UserDefinedType(traitDecl.getOrigin(), 
                    new NameSegment(traitDecl.getOrigin(), nameNode.getValue(), typeArgs));
            var clazz = new ClassDecl(traitDecl.getOrigin(), new Name(nameNode.getOrigin(), 
                    typeDeclarationCompiler.compiler.nameCompiler.CLASS_PREFIX + nameNode.getValue()), null,
                    typeParameters, classMembers, List.of(traitRef), false);
            return List.of(trait, clazz);
        } else {
            return List.of(traitDecl);
        }
    }

    /**
     * To support 'super(...)' calls, we translate each Java constructor to an 'init' method in the Dafny trait
     * The Dafny class constructor then calls the init method of the related trait, and of the trait of its parent type.
     */
    private Method constructorToInitMethod(String className, Constructor constructor) {
        BlockStmt body;
        if (constructor.getBody() == null) {
            body = new BlockStmt(constructor.getOrigin(), null, List.of(),
                    List.of(/*new AssumeStmt()*/));
        } else {
            body = new BlockStmt(constructor.getBody().getOrigin(), null, List.of(),
                    constructor.getBody().getBodyInit());
        }
        Name nameNode = new Name(constructor.getNameNode().getOrigin(), typeDeclarationCompiler.compiler.nameCompiler.getInitMethodName(className, constructor.getNameNode().getValue()));
        var frameExpressions = new ArrayList<>(constructor.getMod().getExpressions());
        var modClause = new Specification<>(frameExpressions, constructor.getMod().getAttributes());
        frameExpressions.add(new FrameExpression(constructor.getOrigin(), new ThisExpr(constructor.getOrigin()), null));
        return new Method(constructor.getOrigin(), nameNode, constructor.getAttributes(),
                constructor.getIsGhost(), constructor.getSignatureEllipsis(), constructor.getTypeArgs(), constructor.getIns(),
                constructor.getReq(), constructor.getEns(), constructor.getReads(), constructor.getDecreases(),
                modClause,
                false, List.of(), body, false);
    }
}
