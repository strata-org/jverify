package com.aws.jverify.verifier.compiler.generator.base.dafny;

import com.aws.jverify.Impure;
import com.aws.jverify.generated.*;
import com.aws.jverify.verifier.compiler.Reporter;
import com.aws.jverify.verifier.compiler.frontend.JVerifyIndex;
import com.aws.jverify.verifier.compiler.simplifications.JVerifyUtils;
import com.aws.jverify.verifier.compiler.simplifications.MethodOrLoopContractCompiler;
import com.aws.jverify.verifier.compiler.simplifications.NameCompiler;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Names;

import javax.lang.model.type.TypeKind;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class PureTypeCompiler {
    private final DafnyGenerator generator;
    private final Reporter reporter;
    final TypeDeclarationCompiler typeDeclarationCompiler;
    final BaseDafnyGenerator compiler;
    private final Symtab symtab;
    private final Names names;
    private final JVerifyUtils jverifyUtils;
    private final NameCompiler nameCompiler;

    public PureTypeCompiler(Context context, TypeDeclarationCompiler typeDeclarationCompiler) {
        this.typeDeclarationCompiler = typeDeclarationCompiler;
        this.compiler = typeDeclarationCompiler.baseGenerator;
        symtab = Symtab.instance(compiler.context);
        names = Names.instance(compiler.context);
        nameCompiler = NameCompiler.instance(compiler.context);
        generator = context.get(DafnyGenerator.class);
        jverifyUtils = context.get(JVerifyUtils.class);
        reporter = Reporter.instance(compiler.context);
    }

    public TopLevelDeclWithMembers translate(JCTree.JCClassDecl classDecl, IOrigin origin, Name name) {

        var classSymbol = classDecl.sym;
        List<JCTree.JCTypeParameter> javaTypeParams = classDecl.typarams;
        if (classDecl.sym.isDirectlyOrIndirectlyLocal()) {
            javaTypeParams = compiler.getOwnAndEnclosedTypeParameters(classDecl.sym).toList();
        }
        var typeParams = typeDeclarationCompiler.translateTypeParameters(javaTypeParams);

        var traits = classSymbol
                .getInterfaces().stream()
                .map(baseType -> generator.translateType(baseType, origin, null))
                .collect(Collectors.toList());

        var superClass = classDecl.sym.getSuperclass();
        var pureObjectType = new UserDefinedType(origin, new NameSegment(origin, BaseDafnyGenerator.PURE_OBJECT_NAME, null));
        if (superClass != null) {
            if (superClass.tsym == symtab.objectType.tsym) {
                traits.addFirst(pureObjectType);
            } if (superClass.getKind() == TypeKind.NONE) {
                if (classSymbol.isInterface()) {
                    traits.addFirst(pureObjectType);
                }
            } else {
                if (JVerifyUtils.typeHasSource(compiler.index, superClass.tsym)) {
                    traits.addFirst(generator.translateType(superClass, origin, null));
                }
            }
        } else {
            traits.addFirst(pureObjectType);
        }

        var comps = TreeInfo.recordFields(classDecl);
        var compNames = comps.stream()
                .map(JCTree.JCVariableDecl::getName)
                .map(com.sun.tools.javac.util.Name::toString)
                .collect(Collectors.toSet());
        var members = new ArrayList<MemberDecl>();

        List<JCTree.JCVariableDecl> fields = new ArrayList<>();
        boolean isAbstract = classSymbol.isAbstract();
        for (var member : classDecl.getMembers()) {
            if (member instanceof JCTree.JCMethodDecl methodDecl) {
                if (TreeInfo.isConstructor(methodDecl)) {
                    translateConstructor(classDecl, origin, methodDecl, members);
                    continue;
                }
                var methodName = methodDecl.getName().toString();
                var params = methodDecl.getParameters();
                if (compNames.contains(methodName) && params.isEmpty()) {
                    reporter.reportError(member, "notSupported", "explicit record component accessor method");
                    continue;
                } else if(symtab.recordType.tsym != classDecl.sym && classSymbol.isRecord()) {
                    if ("equals".equals(methodName)
                            && params.length() == 1
                            && params.getFirst().type.toString().equals(Object.class.getName())) {
                        reporter.reportError(member, "notSupported", "overridden equals method in record");
                        continue;
                    } else if ("hashCode".equals(methodName) && params.isEmpty()) {
                        reporter.reportError(member, "notSupported", "overridden hashCode method in record");
                        continue;
                    }
                }
            } else if (member instanceof JCTree.JCVariableDecl variableDecl) {
                fields.add(variableDecl);
                if (isAbstract) {
                    Name fieldName = nameCompiler.getName(variableDecl, variableDecl.sym);
                    var fieldOrigin = reporter.declToOrigin(variableDecl, fieldName);
                    Type type = generator.translateType(variableDecl.vartype.type, reporter.toOrigin(variableDecl.vartype), variableDecl.getModifiers());
                    members.add(new ConstantField(fieldOrigin, fieldName, null, BaseDafnyGenerator.Ghostness, type, null, false, false));
                }
                continue;
            } 
            var dafnyMember = typeDeclarationCompiler.translateMember(member);
            if (dafnyMember != null) {
                members.add(dafnyMember);
            }
        }

        if (jverifyUtils.isAnnotatedRecursive(classDecl.type, Impure.class)) {
            reporter.reportError(origin, "modifiableForbidden", "a record class");
            traits.clear();
        }

        if (isAbstract) {
            return new TraitDecl(origin, name, null, typeParams, members, traits, false);
        }

        members.addAll(typeDeclarationCompiler.getBodylessMethods(classSymbol, origin, false).values());
        return new IndDatatypeDecl(origin, name, null, typeParams, members, traits,
                List.of(getDatatypeCtor(origin, name, fields)), false);
    }

    private DatatypeCtor getDatatypeCtor(IOrigin origin, Name name, List<JCTree.JCVariableDecl> fields) {
        var ctorParams = fields.stream()
                .map(typeDeclarationCompiler::translateField)
                .map(field -> new Formal(
                        field.getOrigin(), field.getNameNode(),
                        field.getExplicitType(),
                        false, true,
                        null, null,
                        false, false, false,
                        null
                ))
                .toList();
        DatatypeCtor ctor = new DatatypeCtor(
                origin,
                name,
                null,
                false,
                ctorParams
        );
        return ctor;
    }

    private void translateConstructor(JCTree.JCClassDecl classDecl, IOrigin origin,
                                      JCTree.JCMethodDecl methodDecl, ArrayList<MemberDecl> members) {
        if (isCanonicalRecordConstructor(methodDecl)) {
            return;
        }

        NameSegment resultReference = new NameSegment(origin, NameCompiler.RETURN_VARIABLE_NAME, null);

        java.util.function.BiFunction<JCTree.JCIdent, IOrigin, Expression> handleIdentifierOverride = (identifier, innerOrigin) -> {
            if (identifier.sym.owner == classDecl.sym) {
                if (identifier.name == identifier.name.table.names._this) {
                    return resultReference;
                } else {
                    var identName = compiler.nameCompiler.getCompiledName(identifier.sym, origin);
                    return new ExprDotName(origin, resultReference, reporter.getName(identifier, identName), null);
                }
            } else {
                return compiler.expressionCompiler.translateIdentifierNoOverride(identifier, innerOrigin);
            }
        };

        var dafnyMember = compiler.expressionCompiler.withOverrideTranslateIdentifier(
                () -> typeDeclarationCompiler.translateMember(methodDecl),
            handleIdentifierOverride);

        if (dafnyMember instanceof Constructor constructor && 
                (classDecl.sym.isAnonymous() || 
                        JVerifyUtils.isSynthetic(classDecl.sym.flags()) || 
                        !MethodOrLoopContractCompiler.hasImplementation(methodDecl))) {
            Type outType = compiler.translateType(classDecl.type, constructor.getOrigin());
            Formal result = new Formal(origin, new Name(origin, NameCompiler.RETURN_VARIABLE_NAME), outType, false, false, null, null, false, false, false, null);

            List<AttributedExpression> ens = constructor.getEns();
            if (JVerifyUtils.isSynthetic(methodDecl.sym.flags())) {
                ens = classDecl.getMembers().stream().filter(m -> m instanceof JCTree.JCVariableDecl).map(member ->
                {
                    var field = (JCTree.JCVariableDecl)member;
                    IOrigin fieldOrigin = reporter.toOrigin(field);
                    var fieldName = new Name(fieldOrigin, compiler.nameCompiler.getCompiledName(field.sym, fieldOrigin));
                    BinaryExpr e = new BinaryExpr(fieldOrigin, BinaryExprOpcode.Eq, 
                            new ExprDotName(fieldOrigin, resultReference, fieldName, null),
                            new NameSegment(fieldOrigin, fieldName.getValue(), null));
                    return new AttributedExpression(e, null, null);
                }).toList();
            }
            var staticFunction = new Function(constructor.getOrigin(), constructor.getNameNode(), constructor.getAttributes(), 
                    BaseDafnyGenerator.Ghostness, null,
                constructor.getTypeArgs(), constructor.getIns(), constructor.getReq(), ens, constructor.getReads(), constructor.getDecreases(),
            true, false, result, outType, null, null, null);

            members.add(staticFunction);
        } else {
            if (dafnyMember != null) {
                reporter.reportError(methodDecl, "notSupported", "verified explicit record constructor");
            }
        }
    }

    /**
     * Returns whether the declaration is a record's synthetic (implicit) canonical constructor.
     */
    public static boolean isCanonicalRecordConstructor(JCTree.JCMethodDecl methodDecl) {
        if (methodDecl == null) {
            return false;
        }

        return TreeInfo.isCanonicalConstructor(methodDecl) &&
                (methodDecl.mods.flags & Flags.GENERATEDCONSTR) != 0;
    }

    /**
     * Translates the given {@code new RecordType(...)} invocation into a {@link DatatypeValue}
     * that can be used in pure contexts.
     */
    public static Expression translateNewRecord(ExpressionCompiler expressionCompiler, IOrigin origin, JCTree.JCNewClass newClass, ExpressionContext expressionContext) {
        BaseDafnyGenerator compiler = expressionCompiler.baseGenerator;
        var reporter = compiler.reporter;
        List<Type> typeArgs = new ArrayList<>();
        if (newClass.type.tsym.isDirectlyOrIndirectlyLocal()) {
            typeArgs = compiler.getOwnAndEnclosedTypeParameters(newClass.type.tsym).map(
                    tp -> compiler.translateType(tp.type, reporter.toOrigin(tp))).collect(Collectors.toList());
        }
        typeArgs.addAll(newClass.type.getTypeArguments().map(t -> expressionCompiler.baseGenerator.translateType(t, origin)));

        JVerifyIndex index = JVerifyIndex.instance(expressionCompiler.baseGenerator.context);
        boolean callDatatypeConstructor = isCanonicalRecordConstructor((JCTree.JCMethodDecl) index.getTree(newClass.constructor));

        var datatypeName = expressionCompiler.baseGenerator.getNameCompiler().getCompiledName(newClass.constructor.enclClass(), origin);
        var constructorName = callDatatypeConstructor ? datatypeName : expressionCompiler.baseGenerator.getNameCompiler().getCompiledName(newClass.constructor, origin);

        NameSegment datatypeReference = new NameSegment(origin, datatypeName, typeArgs);
        var dafnyConstructor = new ExprDotName(origin, datatypeReference, reporter.getName(newClass, constructorName), null);

        return expressionCompiler.createCall(origin, dafnyConstructor, newClass.args.stream(), expressionContext);
    }
}
