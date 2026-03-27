package com.aws.jverify.laurel;

public sealed interface StmtExpr extends Node permits LiteralBool, Int, Real, String_, Hole, NondetHole, VarDecl, Call, New, FieldAccess, Identifier, Parenthesis, Assign, Add, Sub, Mul, Div, Mod, DivT, ModT, Eq, Neq, Gt, Lt, Le, Ge, And, Or, AndThen, OrElse, Implies, StrConcat, Not, Neg, ForallExpr, ExistsExpr, IfThenElse, Assert, Assume, Return, Block, LabelledBlock, Exit, While, ForLoop, IsType, AsType {}
