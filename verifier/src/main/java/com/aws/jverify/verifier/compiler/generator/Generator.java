package com.aws.jverify.verifier.compiler.generator;

import com.aws.jverify.verifier.VerifierOptions;
import org.checkerframework.checker.nullness.qual.Nullable;

import javax.tools.JavaFileObject;

public interface Generator<TProgram> {

    @Nullable TProgram analyzeJavaCode(VerifierOptions options, java.util.List<JavaFileObject> files);
}
