package com.aws.jverify.laurel;

public sealed interface StmtExpr extends Node permits LiteralBool, Int, VarDecl, Identifier, Parenthesis, Assign, Add, Eq, Neq, Gt, Lt, Le, Ge, Call, IfThenElse, Assert, Assume, Return, Block {}
