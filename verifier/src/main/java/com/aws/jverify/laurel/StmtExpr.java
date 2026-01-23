package com.aws.jverify.laurel;

public sealed interface StmtExpr extends Node permits LiteralBool, Int, VarDecl, Call, FieldAccess, ArrayIndex, Identifier, Parenthesis, Assign, Add, Sub, Mul, Div, Mod, DivT, ModT, Eq, Neq, Gt, Lt, Le, Ge, And, Or, Implies, Not, Neg, ForallExpr, ExistsExpr, IfThenElse, Assert, Assume, Return, Block, While {}
