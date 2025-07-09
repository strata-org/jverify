package com.aws.jverify.verifier.compiler.simplifications.workaround;

import com.aws.jverify.Contract;
import com.aws.jverify.Modifiable;
import com.aws.jverify.Nullable;
import com.aws.jverify.Union;
import com.aws.jverify.generated.*;
import com.aws.jverify.verifier.compiler.ClassCompiler;
import com.aws.jverify.verifier.compiler.JavaToDafnyCompiler;
import com.aws.jverify.verifier.compiler.JavaViolationException;
import com.aws.jverify.verifier.compiler.simplifications.RecordCompiler;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.tree.TreeInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static com.aws.jverify.verifier.compiler.JavaToDafnyCompiler.isInterface;

public class ClassesExtendingClassesCompiler {
    public static final String DAFNY_REFERENCE_BASE_TYPE = "object";
    ClassCompiler classCompiler;

    public ClassesExtendingClassesCompiler(ClassCompiler classCompiler) {
        this.classCompiler = classCompiler;
    }

    public @Nullable List<TopLevelDecl> compile(TopLevelDecl clazz, Symbol.ClassSymbol symbol) {
        if (clazz instanceof ClassDecl classDecl) {
            return buildTraitAndClassTwin(symbol, classDecl.getOrigin(),
                    classDecl.getNameNode(), classDecl.getMembers(), 
                    classDecl.getTypeArgs(), classDecl.getTraits());
        }
        return List.of(clazz);
    }
    
    /**
     * Translating Java classes to both a Dafny trait and a class is used to support classes extending classes
     */
    private List<TopLevelDecl> buildTraitAndClassTwin(Symbol.ClassSymbol classSymbol,
                                                       IOrigin origin, Name name,
                                                       List<MemberDecl> members,
                                                       List<TypeParameter> typeParameters,
                                                       List<Type> superTraits) {
        var traitMembers = new ArrayList<MemberDecl>();
        var classMembers = new ArrayList<MemberDecl>();
        var classNeeded = !JavaToDafnyCompiler.isInterfaceOrAbstract(classSymbol);
        var unionSubsetNeeded = classCompiler.compiler.isAnnotated(classSymbol.type, Union.class);

        for(var member : members) {
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
                    Method initMethod = constructorToInitMethod(name.getValue(), constructor);
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

        Symtab symtab = Symtab.instance(classCompiler.compiler.context);
        if (classSymbol == symtab.objectType.tsym || classSymbol == symtab.recordType.tsym) {
            superTraits = new ArrayList<>();
            superTraits.add(new UserDefinedType(origin, new NameSegment(origin, JavaToDafnyCompiler.REFERENCE_OR_VALUE_OBJECT_NAME, null)));
        }
        
        var mutable = !JavaToDafnyCompiler.isInterface(classSymbol)
                || classCompiler.compiler.isAnnotated(classSymbol.type, Modifiable.class);
        if (mutable) {
            superTraits.add(new UserDefinedType(origin, new NameSegment(origin, DAFNY_REFERENCE_BASE_TYPE, null)));
        }

        var traitDecl = new TraitDecl(origin, name, null, typeParameters, traitMembers, superTraits, false);
        List<Type> typeArgs = typeParameters.stream().map(
                p -> (Type)new UserDefinedType(p.getOrigin(),
                        new NameSegment(p.getOrigin(), p.getNameNode().getValue(), null))).toList();
        var traitType = new UserDefinedType(origin, new NameSegment(origin, name.getValue(), typeArgs));

        var decls = new ArrayList<TopLevelDecl>();
        decls.add(traitDecl);
        if (classNeeded) {
            var clazz = new ClassDecl(origin, new Name(name.getOrigin(), classCompiler.compiler.nameCompiler.CLASS_PREFIX + name.getValue()), null,
                    typeParameters, classMembers, List.of(traitType), false);
            decls.add(clazz);
        }
        if (unionSubsetNeeded) {
            var subset = buildUnionSubsetType(classSymbol, origin, name, typeParameters, traitType);
            if (subset != null) {
                decls.add(subset);
            }
        }
        return decls;
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
        Name nameNode = new Name(constructor.getNameNode().getOrigin(), classCompiler.compiler.nameCompiler.getInitMethodName(className, constructor.getNameNode().getValue()));
        var frameExpressions = new ArrayList<>(constructor.getMod().getExpressions());
        var modClause = new Specification<>(frameExpressions, constructor.getMod().getAttributes());
        frameExpressions.add(new FrameExpression(constructor.getOrigin(), new ThisExpr(constructor.getOrigin()), null));
        return new Method(constructor.getOrigin(), nameNode, constructor.getAttributes(),
                constructor.getIsGhost(), constructor.getSignatureEllipsis(), constructor.getTypeArgs(), constructor.getIns(),
                constructor.getReq(), constructor.getEns(), constructor.getReads(), constructor.getDecreases(),
                modClause,
                false, List.of(), body, false);
    }

    private @Nullable SubsetTypeDecl buildUnionSubsetType(
            Symbol.ClassSymbol classSymbol, IOrigin origin, Name name,
            List<TypeParameter> typeParameters, Type traitType) {
        if (!isSealedInterface(classSymbol)) {
            classCompiler.compiler.reportError(origin, "unionMustBeSealedInterface");
            return null;
        }

        // Any @Contract class must implement the interface and so must appear in the permits clause,
        // but shouldn't be translated to a datatype constructor.
        var variantTypes = classSymbol.getPermittedSubclasses()
                .stream()
                .filter(type -> !classCompiler.compiler.isAnnotated(type, Contract.class))
                .toList();
        if (!variantTypes.stream().allMatch(classCompiler.compiler::isRecord)) {
            classCompiler.compiler.reportError(origin, "unionMustPermitRecords");
            return null;
        }

        var unionName = new Name(origin, classCompiler.compiler.nameCompiler.UNION_PREFIX + name.getValue());
        var varName = new Name(origin, "u");
        var subsetVar = new BoundVar(origin, varName, traitType, false);
        var subsetVarExpr = new IdentifierExpr(subsetVar.getOrigin(), varName.getValue());
        var subsetConstraint = variantTypes.stream()
                .<Expression>map(variantType -> {
                    var dafnyType = classCompiler.compiler.translateType(variantType, origin);
                    return new TypeTestExpr(origin, subsetVarExpr, dafnyType);
                })
                .reduce((a, b) -> new BinaryExpr(origin, BinaryExprOpcode.Or, a, b))
                .orElseThrow(JavaViolationException::new);

        var typeParamCharacteristics = new TypeParameterCharacteristics(
                TypeParameterEqualitySupportValue.Unspecified,
                TypeAutoInitInfo.MaybeEmpty,
                false
        );
        return new SubsetTypeDecl(origin, unionName, null,
                typeParameters, typeParamCharacteristics,
                subsetVar, subsetConstraint,
                SubsetTypeDeclWKind.OptOut, null);
    }

    private static boolean isSealedInterface(Symbol.ClassSymbol classSymbol) {
        return classSymbol.isInterface() && classSymbol.isSealed();
    }

}
