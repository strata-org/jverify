package com.aws.jverify.verifier.compiler.dafnygenerator.base;

import com.aws.jverify.generated.Expression;

record FlowCast(String name, Expression expression, com.aws.jverify.generated.Type type) {}
