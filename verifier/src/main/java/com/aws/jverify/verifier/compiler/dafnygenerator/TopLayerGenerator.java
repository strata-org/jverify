package com.aws.jverify.verifier.compiler.dafnygenerator;

import com.aws.jverify.generated.IOrigin;
import com.aws.jverify.generated.Type;
import com.aws.jverify.verifier.compiler.dafnygenerator.base.BaseDafnyGenerator;
import com.sun.tools.javac.tree.JCTree;

public class TopLayerGenerator extends WrappingDafnyGenerator {
    BaseDafnyGenerator bottomGenerator;
    public TopLayerGenerator(DafnyGenerator original) {
        super(original);
    }

    @Override
    public Type translateClassType(IOrigin origin, JCTree.JCModifiers additionalModifiers, com.sun.tools.javac.code.Type.ClassType classType) {

        // TODO make ModifiableObjectCompiler into a WrappingDafnyGenerator
        Type remappedType = new ModifiableObjectCompiler(bottomGenerator).getRemappedType(classType, origin, additionalModifiers);
        if (remappedType != null) {
            return remappedType;
        }

        // TODO make JVerifyGhostExpressionCompiler into a WrappingDafnyGenerator
        var builtinType = new JVerifyGhostExpressionCompiler(bottomGenerator.expressionCompiler).translateClassType(classType, origin);
        if (builtinType != null) {
            return builtinType;
        }
        return next.translateClassType(origin, additionalModifiers, classType);
    }
}
