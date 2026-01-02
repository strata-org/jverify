package com.aws.jverify.verifier.compiler.generator.laurel;

import com.aws.jverify.laurel.Node;
import com.aws.jverify.verifier.VerifierOptions;
import com.sun.tools.javac.util.Context;

import javax.tools.JavaFileObject;
import java.util.List;

public class JavaToLaurelCompiler {
    public JavaToLaurelCompiler(Context context) {
        
    }

    public Node analyzeJavaCode(VerifierOptions verifierOptions, List<JavaFileObject> readFiles) {
        throw new RuntimeException();
    }
}
