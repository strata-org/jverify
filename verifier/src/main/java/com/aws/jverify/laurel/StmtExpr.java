package com.aws.jverify.laurel;

public sealed interface StmtExpr extends Node permits LiteralBool, Assert, Assume, Block {}
