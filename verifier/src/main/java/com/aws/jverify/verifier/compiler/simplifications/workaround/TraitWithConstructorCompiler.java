package com.aws.jverify.verifier.compiler.simplifications.workaround;

import com.aws.jverify.Nullable;
import com.aws.jverify.generated.*;
import com.aws.jverify.verifier.compiler.dafnygenerator.base.BaseDafnyGenerator;
import com.aws.jverify.verifier.compiler.dafnygenerator.base.TypeDeclarationCompiler;
import com.sun.tools.javac.code.Symbol;

import java.util.*;

/**
 * Since Dafny does not support traits with constructors,
 * this compiler splits such a trait into a trait and a class, moving the constructor to the class
 */
public class TraitWithConstructorCompiler {
    private final TypeDeclarationCompiler typeDeclarationCompiler;

    public TraitWithConstructorCompiler(TypeDeclarationCompiler typeDeclarationCompiler) {
        this.typeDeclarationCompiler = typeDeclarationCompiler;
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

                    var classConstructor = new Constructor(constructor.getOrigin(), constructor.getNameNode(), 
                            null, BaseDafnyGenerator.Ghostness, null,
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
            classMembers.addAll(typeDeclarationCompiler.getBodylessMethods(classSymbol, traitDecl.getOrigin()).values());

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
