package com.aws.jverify.verifier.compiler.generator.laurel;

import com.aws.jverify.laurel.Node;
import com.aws.jverify.laurel.Procedure;
import com.aws.jverify.laurel.Program;
import com.aws.jverify.verifier.VerifierOptions;
import com.aws.jverify.verifier.compiler.frontend.JavaLowerer;
import com.sun.tools.javac.util.Context;

import javax.tools.JavaFileObject;
import java.util.ArrayList;
import java.util.List;

public class JavaToLaurelCompiler {
    private final JavaLowerer lowerer;
    public JavaToLaurelCompiler(Context context) {
        lowerer = new JavaLowerer(context);
    }

    public Node analyzeJavaCode(VerifierOptions verifierOptions, List<JavaFileObject> readFiles) {
        var loweredResult = lowerer.lowerJava(verifierOptions, readFiles);
        List<Procedure> staticProcedures = new ArrayList<>();
        for (var compilationUnit : loweredResult.parsed()) {
        }
        return new Program(null, staticProcedures);
    }
}
