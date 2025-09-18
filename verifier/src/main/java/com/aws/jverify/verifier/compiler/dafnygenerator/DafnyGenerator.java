package com.aws.jverify.verifier.compiler.dafnygenerator;

import com.aws.jverify.generated.*;
import com.aws.jverify.verifier.compiler.dafnygenerator.base.BaseDafnyGenerator;
import com.aws.jverify.verifier.compiler.dafnygenerator.base.BlockCompiler;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.Context;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.ArrayList;
import java.util.Set;

public interface DafnyGenerator {
    FilesContainer compileParsedSet(ArrayList<JCTree.JCCompilationUnit> parsed, Set<JCTree.JCCompilationUnit> libraries);

    Expression translateMethodInvocation(JCTree.JCMethodInvocation invocation, IOrigin origin);
    
    @Nullable
    Type translateType(com.sun.tools.javac.code.Type type, 
                       IOrigin origin, 
                       JCTree.JCModifiers additionalModifiers);

    UserDefinedType translateArrayType(com.sun.tools.javac.code.Type.ArrayType arrayTypeTree, 
                                       IOrigin origin, 
                                       JCTree.JCModifiers additionalModifiers);

    Type translateClassType(IOrigin origin,
                       JCTree.JCModifiers additionalModifiers,
                       com.sun.tools.javac.code.Type.ClassType classType);

    AssignmentRhs translateNewClassToAssignmentRhs(BlockCompiler blockCompiler, JCTree.JCNewClass newClass, IOrigin origin);
    
    static DafnyGenerator getGenerator(Context context) {
        var base = new BaseDafnyGenerator(context);
        var result = new ModifiableObjectGenerator(context, base, 
                new JVerifyGhostExpressionCompiler(new NullableGenerator(base, base), base));
        base.setFinalGenerator(result);
        return result;
    }
}
