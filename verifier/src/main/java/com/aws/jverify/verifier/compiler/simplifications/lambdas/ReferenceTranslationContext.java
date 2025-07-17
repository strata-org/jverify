package com.aws.jverify.verifier.compiler.simplifications.lambdas;

import com.sun.source.tree.MemberReferenceTree;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.List;

import static com.sun.tools.javac.code.Flags.PRIVATE;

/**
 * This class retains all the useful information about a method reference;
 * the contents of this class are filled by the LambdaAnalyzer visitor,
 * and the used by the main translation routines in order to adjust method
 * references (i.e. in case a bridge is needed)
 */
final class ReferenceTranslationContext extends TranslationContext<JCTree.JCMemberReference> {

    final boolean isSuper;

    ReferenceTranslationContext(LambdaAnalyzerPreprocessor lambdaAnalyzerPreprocessor, JCTree.JCMemberReference tree) {
        super(lambdaAnalyzerPreprocessor, tree);
        this.isSuper = tree.hasKind(JCTree.JCMemberReference.ReferenceKind.SUPER);
    }

    boolean needsVarArgsConversion() {
        return tree.varargsElement != null;
    }

    /**
     * @return Is this an array operation like clone()
     */
    boolean isArrayOp() {
        return tree.sym.owner == lambdaAnalyzerPreprocessor.lambdaCompiler.syms.arrayClass;
    }

    boolean receiverAccessible() {
        //hack needed to workaround 292 bug (7087658)
        //when 292 issue is fixed we should remove this and change the backend
        //code to always generate a method handle to an accessible method
        return tree.ownerAccessible;
    }

    /**
     * This method should be called only when target release <= 14
     * where LambdaMetaFactory does not spin nestmate classes.
     * <p>
     * This method should be removed when --release 14 is not supported.
     */
    boolean isPrivateInOtherClass() {
        assert !lambdaAnalyzerPreprocessor.lambdaCompiler.nestmateLambdas;
        return (tree.sym.flags() & PRIVATE) != 0 &&
                !lambdaAnalyzerPreprocessor.lambdaCompiler.types.isSameType(
                        tree.sym.enclClass().asType(),
                        owner.enclClass().asType());
    }

    /**
     * Erasure destroys the implementation parameter subtype
     * relationship for intersection types.
     * Have similar problems for union types too.
     */
    boolean interfaceParameterIsIntersectionOrUnionType() {
        List<Type> tl = tree.getDescriptorType(lambdaAnalyzerPreprocessor.lambdaCompiler.types).getParameterTypes();
        for (; tl.nonEmpty(); tl = tl.tail) {
            Type pt = tl.head;
            if (isIntersectionOrUnionType(pt))
                return true;
        }
        return false;
    }

    boolean isIntersectionOrUnionType(Type t) {
        switch (t.getKind()) {
            case INTERSECTION:
            case UNION:
                return true;
            case TYPEVAR:
                Type.TypeVar tv = (Type.TypeVar) t;
                return isIntersectionOrUnionType(tv.getUpperBound());
        }
        return false;
    }

    /**
     * Does this reference need to be converted to a lambda
     * (i.e. var args need to be expanded or "super" is used)
     */
    final boolean needsConversionToLambda() {
        return interfaceParameterIsIntersectionOrUnionType() ||
                isSuper ||
                needsVarArgsConversion() ||
                isArrayOp() ||
                (!lambdaAnalyzerPreprocessor.lambdaCompiler.nestmateLambdas && isPrivateInOtherClass()) ||
                lambdaAnalyzerPreprocessor.lambdaCompiler.isProtectedInSuperClassOfEnclosingClassInOtherPackage(tree.sym, owner) ||
                !receiverAccessible() ||
                (tree.getMode() == MemberReferenceTree.ReferenceMode.NEW &&
                        tree.kind != JCTree.JCMemberReference.ReferenceKind.ARRAY_CTOR &&
                        (tree.sym.owner.isDirectlyOrIndirectlyLocal() || tree.sym.owner.isInner()));
    }

    Type generatedRefSig() {
        return tree.sym.type;
    }

    Type bridgedRefSig() {
        return lambdaAnalyzerPreprocessor.lambdaCompiler.types.findDescriptorSymbol(tree.target.tsym).type;
    }
}
