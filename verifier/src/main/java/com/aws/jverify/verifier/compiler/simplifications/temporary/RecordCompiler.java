package com.aws.jverify.verifier.compiler.simplifications.temporary;

import com.aws.jverify.Modifiable;
import com.aws.jverify.generated.*;
import com.aws.jverify.verifier.compiler.ClassCompiler;
import com.aws.jverify.verifier.compiler.ExpressionCompiler;
import com.aws.jverify.verifier.compiler.JavaToDafnyCompiler;
import com.sun.source.tree.Tree;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class RecordCompiler {
    final ClassCompiler classCompiler;
    final JavaToDafnyCompiler compiler;

    public RecordCompiler(ClassCompiler classCompiler) {
        this.classCompiler = classCompiler;
        this.compiler = classCompiler.compiler;
    }

    public IndDatatypeDecl translateRecord(JCTree.JCClassDecl classDecl, IOrigin origin, Name name) {
        assert classDecl.getKind() == Tree.Kind.RECORD;
        if (compiler.isAnnotatedRecursive(classDecl.type, Modifiable.class)) {
            compiler.reportError(origin, "modifiableForbidden", "a record class");
        }

        var typeParams = classCompiler.translateTypeParameters(classDecl.typarams);

        var traits = classCompiler.getCurrentTypeSymbol(classDecl)
                .getInterfaces().stream()
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
                    if (!isSyntheticCanonicalConstructor(methodDecl)) {
                        compiler.reportError(member, "notSupported", "explicit record constructor");
                    }
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

    /**
     * Returns whether the declaration is a record's synthetic (implicit) canonical constructor.
     */
    private static boolean isSyntheticCanonicalConstructor(JCTree.JCMethodDecl methodDecl) {
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
    public static DatatypeValue translateNewRecord(ExpressionCompiler expressionCompiler, IOrigin origin, JCTree.JCNewClass newClass) {
        var argBindings = newClass.getArguments().stream()
                .map(a -> new ActualBinding(null, expressionCompiler.toExpr(a), false)).toList();
        var datatypeName = expressionCompiler.compiler.getNameCompiler().getCompiledName(newClass.type.asElement());
        return new DatatypeValue(
                origin, datatypeName, datatypeName,
                new ActualBindings(argBindings));
    }
}
