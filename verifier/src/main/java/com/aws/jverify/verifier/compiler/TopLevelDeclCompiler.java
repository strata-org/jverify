package com.aws.jverify.verifier.compiler;

import com.aws.jverify.Nullable;
import com.aws.jverify.generated.TopLevelDecl;

import java.util.List;

public interface TopLevelDeclCompiler {
    @Nullable
    List<TopLevelDecl> compile(TopLevelDecl clazz);
}
