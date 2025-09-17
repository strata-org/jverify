package com.aws.jverify.verifier.compiler.dafnygenerator;

import com.aws.jverify.generated.FilesContainer;
import com.aws.jverify.generated.IOrigin;
import com.aws.jverify.generated.Type;
import com.aws.jverify.generated.UserDefinedType;
import com.sun.tools.javac.tree.JCTree;
import org.checkerframework.checker.nullness.qual.Nullable;

import javax.lang.model.util.Elements;
import java.util.ArrayList;
import java.util.Set;

public class WrappingDafnyGenerator implements DafnyGenerator {
    protected final DafnyGenerator next;

    public WrappingDafnyGenerator(DafnyGenerator next) {
        this.next = next;
    }

    @Override
    public FilesContainer compileParsedSet(ArrayList<JCTree.JCCompilationUnit> parsed, Set<JCTree.JCCompilationUnit> libraries) {
        return next.compileParsedSet(parsed, libraries);
    }

    @Override
    public @Nullable Type translateType(com.sun.tools.javac.code.Type type, 
                                        IOrigin origin, 
                                        JCTree.JCModifiers additionalModifiers) {
        return next.translateType(type, origin, additionalModifiers);
    }

    @Override
    public UserDefinedType translateArrayType(com.sun.tools.javac.code.Type.ArrayType arrayTypeTree, 
                                              IOrigin origin, 
                                              JCTree.JCModifiers additionalModifiers) {
        return next.translateArrayType(arrayTypeTree, origin, additionalModifiers);
    }

    @Override
    public Type translateClassType(IOrigin origin, JCTree.JCModifiers additionalModifiers, com.sun.tools.javac.code.Type.ClassType classType) {
        return next.translateClassType(origin, additionalModifiers, classType);
    }
}
