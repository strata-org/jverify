package com.aws.jverify.verifier.compiler.simplifications;

import com.aws.jverify.Modifiable;
import com.aws.jverify.generated.*;
import com.aws.jverify.verifier.compiler.ClassCompiler;
import com.aws.jverify.verifier.compiler.ExpressionCompiler;
import com.aws.jverify.verifier.compiler.JavaToDafnyCompiler;
import com.sun.source.tree.Tree;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RecordCompiler {
    final ClassCompiler classCompiler;
    final JavaToDafnyCompiler compiler;

    public RecordCompiler(ClassCompiler classCompiler) {
        this.classCompiler = classCompiler;
        this.compiler = classCompiler.compiler;
    }

    public IndDatatypeDecl translateValueType(JCTree.JCClassDecl classDecl, IOrigin origin, Name name) {
        assert classDecl.getKind() == Tree.Kind.RECORD;
        if (compiler.isAnnotatedRecursive(classDecl.type, Modifiable.class)) {
            compiler.reportError(origin, "modifiableForbidden", "a record class");
        }

        var typeParams = classCompiler.translateTypeParameters(classDecl.typarams);

        Symbol.ClassSymbol currentTypeSymbol = classCompiler.getCurrentTypeSymbol(classDecl);
        var traits = Stream.concat(Stream.of(currentTypeSymbol.getSuperclass()), currentTypeSymbol
                .getInterfaces().stream())
                .filter(compiler::typeHasAContract)
                .map(baseType -> compiler.translateType(null, baseType, origin))
                .toList();

        var comps = TreeInfo.recordFields(classDecl);
        var ctorParams = comps.stream()
                .map(classCompiler::translateField)
                .map(field -> new Formal(
                        field.getOrigin(), field.getNameNode(),
                        field.getExplicitType(),
                        false, true,
                        null, null,
                        false, false, false,
                        null
                ))
                .toList();
        var ctors = List.of(new DatatypeCtor(
                origin,
                name,
                null,
                false,
                ctorParams
        ));

        var compNames = comps.stream()
                .map(JCTree.JCVariableDecl::getName)
                .map(com.sun.tools.javac.util.Name::toString)
                .collect(Collectors.toSet());
        var members = new ArrayList<MemberDecl>();
        List<JCTree.JCVariableDecl> fields = new ArrayList<>();
        for (var member : classDecl.getMembers()) {
            if (member instanceof JCTree.JCVariableDecl varDecl
                    && compNames.contains(varDecl.getName().toString())) {
                fields.add(varDecl);
            }
        }
        for (var member : classDecl.getMembers()) {
            if (member instanceof JCTree.JCVariableDecl varDecl
                    && compNames.contains(varDecl.getName().toString()) ) {
                // Don't translate fields that arise from record components
                continue;
            } else if (member instanceof JCTree.JCMethodDecl methodDecl) {
                // No constructors should be translated:
                // explicit constructors are not allowed/supported,
                // and the implicit canonical constructor is unneeded to construct datatype values.
                if (TreeInfo.isConstructor(methodDecl)) {
                    translateConstructor(classDecl, origin, methodDecl, members);
                    continue;
                }
                var methodName = methodDecl.getName().toString();
                var params = methodDecl.getParameters();
                if (compNames.contains(methodName) && params.isEmpty()) {
                    compiler.reportError(member, "notSupported", "explicit record component accessor method");
                    continue;
                } else if ("equals".equals(methodName)
                        && params.length() == 1
                        && params.getFirst().type.toString().equals(Object.class.getName())) {
                    compiler.reportError(member, "notSupported", "overridden equals method in record");
                    continue;
                } else if ("hashCode".equals(methodName) && params.isEmpty()) {
                    compiler.reportError(member, "notSupported", "overridden hashCode method in record");
                    continue;
                }
            }
            var dafnyMember = classCompiler.translateMember(member);
            if (dafnyMember != null) {
                members.add(dafnyMember);
            }
        }

        return new IndDatatypeDecl(origin, name, null, typeParams, members, traits, ctors, false);
    }

    private void translateConstructor(JCTree.JCClassDecl classDecl, IOrigin origin, 
                                      JCTree.JCMethodDecl methodDecl, ArrayList<MemberDecl> members) {
        String resultName = "resultName";
        NameSegment resultReference = new NameSegment(origin, resultName, null);
        compiler.expressionCompiler.handleIdentifier = (identifier, innerOrigin) -> {
            if (identifier.sym.owner == classDecl.sym) {
                if (identifier.name == identifier.name.table.names._this) {
                    return resultReference;
                } else {
                    var identName = compiler.nameCompiler.getCompiledName(identifier.sym);
                    return new ExprDotName(origin, resultReference, compiler.getName(identifier, identName), null);
                }
            } else {
                return compiler.expressionCompiler.translateIdentifierNoOverride(identifier, innerOrigin);
            }
        };
        boolean isImplicitCanonicalConstructor = isImplicitCanonicalConstructor(methodDecl);
        var shouldVerify = compiler.verifyAnnotationCompiler.shouldVerify() && !isImplicitCanonicalConstructor;
        var dafnyMember = compiler.withSkipDiagnostics(() -> classCompiler.translateMember(methodDecl), isImplicitCanonicalConstructor);
        compiler.expressionCompiler.handleIdentifier = null;
        if (dafnyMember instanceof Constructor constructor && (constructor.getBody() == null || !shouldVerify)) {

            Type outType = compiler.translateType(classDecl.type, constructor.getOrigin());
            Formal result = new Formal(origin, new Name(origin, resultName), outType, false, false, null, null, false, false, false, null);
            var staticFunction = new Function(constructor.getOrigin(), constructor.getNameNode(), constructor.getAttributes(), false, null,
                constructor.getTypeArgs(), constructor.getIns(), constructor.getReq(), constructor.getEns(), constructor.getReads(), constructor.getDecreases(),
            true, false, result, outType, null, null, null);
            members.add(staticFunction);
        } else {
            if (dafnyMember != null) {
                compiler.reportError(methodDecl, "notSupported", "verified explicit record constructor");
            }
        }
    }

    /**
     * Returns whether the declaration is a record's synthetic (implicit) canonical constructor.
     */
    public static boolean isImplicitCanonicalConstructor(JCTree.JCMethodDecl methodDecl) {
        if (methodDecl == null) {
            return false;
        }
        
        // Ideally we'd check for the SYNTHETIC flag, but it's not set.
        // So instead we check for its body: just a lone "super()" call.
        var body = methodDecl.getBody().getStatements();
        return TreeInfo.isCanonicalConstructor(methodDecl)
                && body.length() == 1
                && body.getFirst() instanceof JCTree.JCExpressionStatement stmt
                && TreeInfo.isSuperCall(stmt)
                && TreeInfo.args(stmt.getExpression()).isEmpty();
    }

    /**
     * Translates the given {@code new RecordType(...)} invocation into a {@link DatatypeValue}
     * that can be used in pure contexts.
     */
    public static Expression translateNewRecord(ExpressionCompiler expressionCompiler, IOrigin origin, JCTree.JCNewClass newClass) {
        var argBindings = newClass.getArguments().stream()
                .map(a -> new ActualBinding(null, expressionCompiler.toExpr(a), false)).toList();
        com.sun.tools.javac.util.List<Type> typeArgs = newClass.typeargs.map(expressionCompiler.compiler::translateType);
        if (newClass.clazz instanceof JCTree.JCTypeApply typeApply) {
            typeArgs = typeArgs.appendList(typeApply.arguments.map(expressionCompiler.compiler::translateType));
        }

        JavacTrees trees = JavacTrees.instance(expressionCompiler.compiler.context);
        boolean callDatatypeConstructor = isImplicitCanonicalConstructor((JCTree.JCMethodDecl) trees.getTree(newClass.constructor));
            
        var datatypeName = expressionCompiler.compiler.getNameCompiler().getCompiledName(newClass.constructor.enclClass());
        var constructorName = callDatatypeConstructor ? datatypeName : expressionCompiler.compiler.getNameCompiler().getCompiledName(newClass.constructor);

        NameSegment datatypeReference = new NameSegment(origin, datatypeName, typeArgs);
        var dafnyConstructor = new ExprDotName(origin, datatypeReference, expressionCompiler.compiler.getName(newClass, constructorName), null);
        return new ApplySuffix(origin, dafnyConstructor, null, new ActualBindings(argBindings), null);
    }
}
