package com.aws.jverify.verifier.compiler.dafnygenerator;

import com.aws.jverify.generated.FilesContainer;
import com.aws.jverify.generated.IOrigin;
import com.aws.jverify.generated.Type;
import com.aws.jverify.generated.UserDefinedType;
import com.aws.jverify.verifier.compiler.dafnygenerator.base.BaseDafnyGenerator;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.Context;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.ArrayList;
import java.util.Set;

public interface DafnyGenerator {
    FilesContainer compileParsedSet(ArrayList<JCTree.JCCompilationUnit> parsed, Set<JCTree.JCCompilationUnit> libraries);
        
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
    
    static DafnyGenerator getGenerator(Context context) {
        var base = new BaseDafnyGenerator(context);
        var result = new TopLayerGenerator(new NullableGenerator(base, base));
        base.setFinalGenerator(result);
        return result;
    }
}
