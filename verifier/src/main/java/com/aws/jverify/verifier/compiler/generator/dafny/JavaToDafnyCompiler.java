package com.aws.jverify.verifier.compiler.generator.dafny;

import com.aws.jverify.generated.*;
import com.aws.jverify.verifier.VerifierOptions;
import com.aws.jverify.verifier.compiler.frontend.JavaLowerer;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.Context;
import org.checkerframework.checker.nullness.qual.Nullable;

import javax.tools.JavaFileObject;
import java.net.URI;

public class JavaToDafnyCompiler {
    public final Context context;

    private final DafnyGenerator dafnyGenerator;
    private final JavaLowerer lowerer;

    public JavaToDafnyCompiler(Context context) {
        this.context = context;
        context.put(JavaToDafnyCompiler.class, this);
        lowerer = context.get(JavaLowerer.class);
        this.dafnyGenerator = DafnyGenerator.getGenerator(context);
    }

    public @Nullable FilesContainer analyzeJavaCode(VerifierOptions options, java.util.List<JavaFileObject> files) {
        var loweredResult = lowerer.lowerJava(options, files);
        return dafnyGenerator.generateDafny(loweredResult.parsed(), loweredResult.libraries());
    }

    public boolean isContractSource(JCTree.JCCompilationUnit compilationUnit) {
        return lowerer.isContractSource(compilationUnit);
    }

    public boolean isContractSource(URI sourceURI) {
        return lowerer.isContractSource(sourceURI);
    }
}
