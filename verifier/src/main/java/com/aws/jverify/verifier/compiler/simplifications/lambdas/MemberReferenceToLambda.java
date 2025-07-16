package com.aws.jverify.verifier.compiler.simplifications.lambdas;

import com.sun.source.tree.MemberReferenceTree;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;

import javax.lang.model.type.TypeKind;

import static com.sun.tools.javac.code.Flags.PARAMETER;
import static com.sun.tools.javac.code.Flags.SYNTHETIC;

/**
 * Converts a method reference which cannot be used directly into a lambda
 */
class MemberReferenceToLambda {

    private final LambdaCompiler lambdaCompiler;
    private final JCTree.JCMemberReference tree;
    private final LambdaAnalyzerPreprocessor.ReferenceTranslationContext localContext;
    private final Symbol owner;
    private final ListBuffer<JCTree.JCExpression> args = new ListBuffer<>();
    private final ListBuffer<JCTree.JCVariableDecl> params = new ListBuffer<>();

    private JCTree.JCExpression receiverExpression = null;

    public MemberReferenceToLambda(LambdaCompiler lambdaCompiler, JCTree.JCMemberReference tree, 
                                   LambdaAnalyzerPreprocessor.ReferenceTranslationContext localContext, 
                                   Symbol owner) {
        this.lambdaCompiler = lambdaCompiler;
        this.tree = tree;
        this.localContext = localContext;
        this.owner = owner;
    }

    JCTree.JCLambda lambda() {
        int prevPos = lambdaCompiler.make.pos;
        try {
            lambdaCompiler.make.at(tree);

            //body generation - this can be either a method call or a
            //new instance creation expression, depending on the member reference kind
            Symbol.VarSymbol rcvr = addParametersReturnReceiver();
            JCTree.JCExpression expr = (tree.getMode() == MemberReferenceTree.ReferenceMode.INVOKE)
                    ? expressionInvoke(rcvr)
                    : expressionNew();

            JCTree.JCLambda slam = lambdaCompiler.make.Lambda(params.toList(), expr);
            slam.target = tree.target;
            slam.type = tree.type;
            slam.pos = tree.pos;
            return slam;
        } finally {
            lambdaCompiler.make.at(prevPos);
        }
    }

    /**
     * Generate the parameter list for the converted member reference.
     *
     * @return The receiver variable symbol, if any
     */
    Symbol.VarSymbol addParametersReturnReceiver() {
        Type samDesc = localContext.bridgedRefSig();
        List<Type> samPTypes = samDesc.getParameterTypes();
        List<Type> descPTypes = tree.getDescriptorType(lambdaCompiler.types).getParameterTypes();

        // Determine the receiver, if any
        Symbol.VarSymbol rcvr;
        switch (tree.kind) {
            case BOUND:
                // The receiver is explicit in the method reference
                rcvr = addParameter("rec$", tree.getQualifierExpression().type, false);
                receiverExpression = lambdaCompiler.attr.makeNullCheck(tree.getQualifierExpression());
                break;
            case UNBOUND:
                // The receiver is the first parameter, extract it and
                // adjust the SAM and unerased type lists accordingly
                rcvr = addParameter("rec$", samDesc.getParameterTypes().head, false);
                samPTypes = samPTypes.tail;
                descPTypes = descPTypes.tail;
                break;
            default:
                rcvr = null;
                break;
        }
        List<Type> implPTypes = tree.sym.type.getParameterTypes();
        int implSize = implPTypes.size();
        int samSize = samPTypes.size();
        // Last parameter to copy from referenced method, exclude final var args
        int last = localContext.needsVarArgsConversion() ? implSize - 1 : implSize;

        // Failsafe -- assure match-up
        boolean checkForIntersection = tree.varargsElement != null || implSize == descPTypes.size();

        // Use parameter types of the implementation method unless the unerased
        // SAM parameter type is an intersection type, in that case use the
        // erased SAM parameter type so that the supertype relationship
        // the implementation method parameters is not obscured.
        // Note: in this loop, the lists implPTypes, samPTypes, and descPTypes
        // are used as pointers to the current parameter type information
        // and are thus not usable afterwards.
        for (int i = 0; implPTypes.nonEmpty() && i < last; ++i) {
            // By default use the implementation method parameter type
            Type parmType = implPTypes.head;
            if (checkForIntersection) {
                if (descPTypes.head.getKind() == TypeKind.INTERSECTION) {
                    parmType = samPTypes.head;
                }
                // If the unerased parameter type is a type variable whose
                // bound is an intersection (eg. <T extends A & B>) then
                // use the SAM parameter type
                if (descPTypes.head.getKind() == TypeKind.TYPEVAR) {
                    Type.TypeVar tv = (Type.TypeVar) descPTypes.head;
                    if (tv.getUpperBound().getKind() == TypeKind.INTERSECTION) {
                        parmType = samPTypes.head;
                    }
                }
            }
            addParameter("x$" + i, parmType, true);

            // Advance to the next parameter
            implPTypes = implPTypes.tail;
            samPTypes = samPTypes.tail;
            descPTypes = descPTypes.tail;
        }
        // Flatten out the var args
        for (int i = last; i < samSize; ++i) {
            addParameter("xva$" + i, tree.varargsElement, true);
        }

        return rcvr;
    }

    JCTree.JCExpression getReceiverExpression() {
        return receiverExpression;
    }

    private JCTree.JCExpression makeReceiver(Symbol.VarSymbol rcvr) {
        if (rcvr == null) return null;
        JCTree.JCExpression rcvrExpr = lambdaCompiler.makeIdent(rcvr);
        boolean protAccess =
                lambdaCompiler.isProtectedInSuperClassOfEnclosingClassInOtherPackage(tree.sym, owner);
        Type rcvrType = tree.ownerAccessible && !protAccess ? tree.sym.enclClass().type
                : tree.expr.type;
        if (rcvrType == lambdaCompiler.syms.arrayClass.type) {
            // Map the receiver type to the actually type, not just "array"
            rcvrType = tree.getQualifierExpression().type;
        }
        if (!rcvr.type.tsym.isSubClass(rcvrType.tsym, lambdaCompiler.types)) {
            rcvrExpr = lambdaCompiler.make.TypeCast(lambdaCompiler.make.Type(rcvrType), rcvrExpr).setType(rcvrType);
        }
        return rcvrExpr;
    }

    /**
     * determine the receiver of the method call - the receiver can
     * be a type qualifier, the synthetic receiver parameter or 'super'.
     */
    private JCTree.JCExpression expressionInvoke(Symbol.VarSymbol rcvr) {
        JCTree.JCExpression qualifier =
                (rcvr != null) ?
                        makeReceiver(rcvr) :
                        tree.getQualifierExpression();

        //create the qualifier expression
        JCTree.JCFieldAccess select = lambdaCompiler.make.Select(qualifier, tree.sym);
        select.sym = tree.sym;
        select.type = tree.sym.erasure(lambdaCompiler.types);

        //create the method call expression
        JCTree.JCExpression apply = lambdaCompiler.make.Apply(List.nil(), select,
                        lambdaCompiler.convertArgs(tree.sym, args.toList(), tree.varargsElement)).
                setType(tree.sym.erasure(lambdaCompiler.types).getReturnType());

        apply = lambdaCompiler.transTypes.coerce(lambdaCompiler.attrEnv, apply,
                localContext.tree.referentType.getReturnType());

        lambdaCompiler.setVarargsIfNeeded(apply, tree.varargsElement);
        return apply;
    }

    /**
     * Lambda body to use for a 'new'.
     */
    private JCTree.JCExpression expressionNew() {
        if (tree.kind == JCTree.JCMemberReference.ReferenceKind.ARRAY_CTOR) {
            //create the array creation expression
            JCTree.JCNewArray newArr = lambdaCompiler.make.NewArray(
                    lambdaCompiler.make.Type(lambdaCompiler.types.elemtype(tree.getQualifierExpression().type)),
                    List.of(lambdaCompiler.makeIdent(params.first().sym)),
                    null);
            newArr.type = tree.getQualifierExpression().type;
            return newArr;
        } else {
            //create the instance creation expression
            //note that method reference syntax does not allow an explicit
            //enclosing class (so the enclosing class is null)
            // but this may need to be patched up later with the proxy for the outer this
            JCTree.JCNewClass newClass = lambdaCompiler.make.NewClass(null,
                    List.nil(),
                    lambdaCompiler.make.Type(tree.getQualifierExpression().type),
                    lambdaCompiler.convertArgs(tree.sym, args.toList(), tree.varargsElement),
                    null);
            newClass.constructor = tree.sym;
            newClass.constructorType = tree.sym.erasure(lambdaCompiler.types);
            newClass.type = tree.getQualifierExpression().type;
            lambdaCompiler.setVarargsIfNeeded(newClass, tree.varargsElement);
            return newClass;
        }
    }

    private Symbol.VarSymbol addParameter(String name, Type p, boolean genArg) {
        Symbol.VarSymbol vsym = new Symbol.VarSymbol(PARAMETER | SYNTHETIC, lambdaCompiler.names.fromString(name), p, owner);
        vsym.pos = tree.pos;
        params.append(lambdaCompiler.make.VarDef(vsym, null));
        if (genArg) {
            args.append(lambdaCompiler.makeIdent(vsym));
        }
        return vsym;
    }
}

