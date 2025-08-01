package com.aws.jverify.verifier.compiler.simplifications.workaround;

import com.aws.jverify.Nullable;
import com.aws.jverify.generated.*;
import com.aws.jverify.verifier.compiler.TypeDeclarationCompiler;
import com.aws.jverify.verifier.compiler.JavaToDafnyCompiler;
import com.sun.tools.javac.code.Symbol;

import java.util.ArrayList;
import java.util.List;

/**
 * Since Dafny does not support traits with constructors,
 * this compiler splits such a trait into a trait and a class, moving the constructor to the class
 */
public class TraitWithConstructorCompiler {
    TypeDeclarationCompiler typeDeclarationCompiler;

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
        var classNeeded = !JavaToDafnyCompiler.isInterfaceOrAbstract(classSymbol);

        for(var member : traitDecl.getMembers()) {
            switch (member) {
                case Method method when !method.getHasStaticKeyword() -> {
                    traitMembers.add(member);
                    if (method.getBody() == null) {
                        classMembers.add(member);
                        // A bodyless trait in Dafny is abstract. 
                        // You can not declare an assumed member in traits in Dafny
                        // We add the assumed member to the class
                    }
                }

                case Function function -> {
                    traitMembers.add(function);
                    if (function.getBody() == null && !function.getHasStaticKeyword()) {
                        // A bodyless trait in Dafny is abstract.
                        // You can not declare an assumed member in traits in Dafny
                        // We add the assumed member to the class
                        classMembers.add(member);
                    }
                }
                case Constructor constructor -> {
                    classNeeded = true;
                    Method initMethod = constructorToInitMethod(traitDecl.getNameNode().getValue(), constructor);
                    if (initMethod != null) {
                        traitMembers.add(initMethod);
                    }

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
        if (constructor.getBody() == null) {
            return null;
        }
        BlockStmt body = new BlockStmt(constructor.getBody().getOrigin(), null, List.of(),
                constructor.getBody().getBodyInit());
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
