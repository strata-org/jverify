package com.aws.jverify.verifier.compiler.simplifications.workaround;

import com.aws.jverify.Modifiable;
import com.aws.jverify.Nullable;
import com.aws.jverify.generated.*;
import com.aws.jverify.verifier.compiler.ClassCompiler;
import com.aws.jverify.verifier.compiler.JavaToDafnyCompiler;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symtab;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static com.aws.jverify.verifier.compiler.JavaToDafnyCompiler.isInterface;

public class ClassesExtendingClassesCompiler {
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
            superTraits.clear();
            superTraits.add(new UserDefinedType(origin, new NameSegment(origin, "ValueObject", null)));
        }
        
        if ((!JavaToDafnyCompiler.isInterface(classSymbol) && classSymbol != symtab.recordType.tsym) 
                || classCompiler.compiler.isAnnotated(classSymbol.type, Modifiable.class)) {
            superTraits.add(new UserDefinedType(origin, new NameSegment(origin, "object", null)));
        }

        var trait = new TraitDecl(origin, name, null, typeParameters, traitMembers, superTraits, false);
        List<Type> typeArgs = typeParameters.stream().map(
                p -> (Type)new UserDefinedType(p.getOrigin(),
                        new NameSegment(p.getOrigin(), p.getNameNode().getValue(), null))).toList();

        if (classNeeded) {
            var clazz = new ClassDecl(origin, new Name(name.getOrigin(), classCompiler.compiler.nameCompiler.CLASS_PREFIX + name.getValue()), null,
                    typeParameters, classMembers, List.of(new UserDefinedType(origin, new NameSegment(origin, name.getValue(), typeArgs))), false);
            return List.of(trait, clazz);
        } else {
            return List.of(trait);
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
}
