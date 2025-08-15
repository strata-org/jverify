package com.aws.jverify.verifier.compiler.simplifications;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.*;

public class MethodReferenceToLambdaCompiler {
    private final TreeMaker make;
    private final Names names;
    private final Types types;

    public MethodReferenceToLambdaCompiler(Context context) {
        this.make = TreeMaker.instance(context);
        this.names = Names.instance(context);
        this.types = Types.instance(context);
    }

    public JCTree.JCLambda referenceToLambda(JCTree.JCMemberReference reference) {
        var paramTypes = types.findDescriptorType(reference.type).getParameterTypes();
        var params = getParameters(paramTypes).toList();

        JCTree.JCExpression methodCall = switch (reference.kind) {
            case STATIC ->
                // Static method reference: Class::method -> (args) -> Class.method(args)
                    replaceColonsWithDot(reference, params);
            case BOUND ->
                // Bound instance method: obj::method -> (args) -> obj.method(args)
                    replaceColonsWithDot(reference, params);
            case UNBOUND ->
                // Unbound instance method: Class::method -> (obj, args) -> obj.method(args)
                    addImplicitReceiverArgument(reference, params);
            case IMPLICIT_INNER ->
                // Constructor reference for inner class: Outer.Inner::new -> (args) -> new Outer.Inner(args)
                    useLhsToConstructNewClass(reference, params);
            default ->
                // Constructor reference: Class::new -> (args) -> new Class(args)
                    useLhsToConstructNewClass(reference, params);
        };

        make.pos = reference.pos;
        var lambda = make.Lambda(params, methodCall);
        lambda.type = reference.type;
        return lambda;
    }

    private JCTree.JCExpression useLhsToConstructNewClass(JCTree.JCMemberReference reference, List<JCTree.JCVariableDecl> params) {
        JCTree.JCNewClass newClass = make.NewClass(
                null,
                null,
                reference.expr,
                makeIdentList(params),
                null
        );
        newClass.constructor = reference.sym;
        newClass.type = reference.sym.owner.type;
        return newClass;
    }

    private JCTree.JCExpression addImplicitReceiverArgument(JCTree.JCMemberReference reference, 
                                                            List<JCTree.JCVariableDecl> params) {
        JCTree.JCIdent receiver = make.Ident(params.head.name);
        receiver.sym = params.head.sym;
        receiver.type = params.head.type;

        JCTree.JCFieldAccess select = make.Select(receiver, reference.name);
        select.sym = reference.sym;
        select.type = reference.sym.type;

        List<JCTree.JCExpression> args = makeIdentList(params.tail);
        var methodCall = make.Apply(
                null,
                select,
                args
        );
        
        methodCall.type = reference.sym.type.getReturnType();
        return methodCall;
    }

    private JCTree.JCExpression replaceColonsWithDot(JCTree.JCMemberReference reference, 
                                                     Iterable<JCTree.JCVariableDecl> params) {
        JCTree.JCFieldAccess select = make.Select(reference.expr, reference.name);
        select.sym = reference.sym;
        select.type = reference.sym.type;

        var methodCall = make.Apply(
                null,
                select,
                makeIdentList(params)
        );
        methodCall.type = reference.sym.type.getReturnType();
        return methodCall;
    }

    private ListBuffer<JCTree.JCVariableDecl> getParameters(List<Type> paramTypes) {
        ListBuffer<JCTree.JCVariableDecl> params = new ListBuffer<>();
        for (int i = 0; i < paramTypes.size(); i++) {
            Name paramName = names.fromString("x" + i);
            JCTree.JCVariableDecl param = make.VarDef(
                    make.Modifiers(0),
                    paramName,
                    make.Type(paramTypes.get(i)),
                    null
            );
            param.sym = new Symbol.VarSymbol(0, paramName, paramTypes.get(i), null);
            params.append(param);
        }
        return params;
    }


    private List<JCTree.JCExpression> makeIdentList(Iterable<JCTree.JCVariableDecl> params) {
        ListBuffer<JCTree.JCExpression> idents = new ListBuffer<>();
        for (JCTree.JCVariableDecl param : params) {
            JCTree.JCIdent ident = make.Ident(param.name);
            ident.sym = param.sym;
            ident.type = param.sym.type;
            idents.append(ident);
        }
        return idents.toList();
    }
}
