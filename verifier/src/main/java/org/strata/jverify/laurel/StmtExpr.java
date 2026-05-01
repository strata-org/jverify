package org.strata.jverify.laurel;

public sealed interface StmtExpr permits StmtExpr.LiteralBool, StmtExpr.Int, StmtExpr.Real, StmtExpr.String_, StmtExpr.Hole, StmtExpr.NondetHole, StmtExpr.VarDecl, StmtExpr.Call, StmtExpr.New, StmtExpr.FieldAccess, StmtExpr.Identifier, StmtExpr.Parenthesis, StmtExpr.Assign, StmtExpr.Add, StmtExpr.Sub, StmtExpr.Mul, StmtExpr.Div, StmtExpr.Mod, StmtExpr.DivT, StmtExpr.ModT, StmtExpr.Eq, StmtExpr.Neq, StmtExpr.Gt, StmtExpr.Lt, StmtExpr.Le, StmtExpr.Ge, StmtExpr.And, StmtExpr.Or, StmtExpr.AndThen, StmtExpr.OrElse, StmtExpr.Implies, StmtExpr.StrConcat, StmtExpr.Not, StmtExpr.Neg, StmtExpr.ForallExpr, StmtExpr.ExistsExpr, StmtExpr.IfThenElse, StmtExpr.Assert, StmtExpr.Assume, StmtExpr.Return, StmtExpr.Block, StmtExpr.LabelledBlock, StmtExpr.Exit, StmtExpr.While, StmtExpr.ForLoop, StmtExpr.IsType, StmtExpr.AsType {
    com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion);

    public record LiteralBool(SourceRange sourceRange, boolean b) implements StmtExpr {
        @Override public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
            var sexp = ion.newEmptySexp(); sexp.add(ion.newSymbol("literalBool"));
            sexp.add(sourceRange.toIon(ion)); sexp.add(ion.newBool(b)); return sexp;
        }
    }

    public record Int(SourceRange sourceRange, long n) implements StmtExpr {
        @Override public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
            var sexp = ion.newEmptySexp(); sexp.add(ion.newSymbol("int"));
            sexp.add(sourceRange.toIon(ion)); sexp.add(ion.newInt(n)); return sexp;
        }
    }

    public record Real(SourceRange sourceRange, java.math.BigDecimal d) implements StmtExpr {
        @Override public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
            var sexp = ion.newEmptySexp(); sexp.add(ion.newSymbol("real"));
            sexp.add(sourceRange.toIon(ion)); sexp.add(ion.newDecimal(d)); return sexp;
        }
    }

    public record String_(SourceRange sourceRange, java.lang.String s) implements StmtExpr {
        @Override public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
            var sexp = ion.newEmptySexp(); sexp.add(ion.newSymbol("string"));
            sexp.add(sourceRange.toIon(ion)); sexp.add(ion.newString(s)); return sexp;
        }
    }

    public record Hole(SourceRange sourceRange) implements StmtExpr {
        @Override public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
            var sexp = ion.newEmptySexp(); sexp.add(ion.newSymbol("hole"));
            sexp.add(sourceRange.toIon(ion)); return sexp;
        }
    }

    public record NondetHole(SourceRange sourceRange) implements StmtExpr {
        @Override public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
            var sexp = ion.newEmptySexp(); sexp.add(ion.newSymbol("nondetHole"));
            sexp.add(sourceRange.toIon(ion)); return sexp;
        }
    }

    public record VarDecl(SourceRange sourceRange, java.lang.String name, TypeAnnotation varType, Initializer assignment) implements StmtExpr {
        @Override public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
            var sexp = ion.newEmptySexp(); sexp.add(ion.newSymbol("varDecl"));
            sexp.add(sourceRange.toIon(ion)); sexp.add(ion.newString(name));
            sexp.add(varType != null ? varType.toIon(ion) : ion.newNull());
            sexp.add(assignment != null ? assignment.toIon(ion) : ion.newNull());
            return sexp;
        }
    }

    public record Call(SourceRange sourceRange, StmtExpr callee, java.util.List<StmtExpr> args) implements StmtExpr {
        @Override public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
            var sexp = ion.newEmptySexp(); sexp.add(ion.newSymbol("call"));
            sexp.add(sourceRange.toIon(ion)); sexp.add(callee.toIon(ion));
            var _l = ion.newEmptyList(); for (var e : args) _l.add(e.toIon(ion));
            sexp.add(_l); return sexp;
        }
    }

    public record New(SourceRange sourceRange, java.lang.String name) implements StmtExpr {
        @Override public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
            var sexp = ion.newEmptySexp(); sexp.add(ion.newSymbol("new"));
            sexp.add(sourceRange.toIon(ion)); sexp.add(ion.newString(name)); return sexp;
        }
    }

    public record FieldAccess(SourceRange sourceRange, StmtExpr obj, java.lang.String field) implements StmtExpr {
        @Override public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
            var sexp = ion.newEmptySexp(); sexp.add(ion.newSymbol("fieldAccess"));
            sexp.add(sourceRange.toIon(ion)); sexp.add(obj.toIon(ion)); sexp.add(ion.newString(field)); return sexp;
        }
    }

    public record Identifier(SourceRange sourceRange, java.lang.String name) implements StmtExpr {
        @Override public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
            var sexp = ion.newEmptySexp(); sexp.add(ion.newSymbol("identifier"));
            sexp.add(sourceRange.toIon(ion)); sexp.add(ion.newString(name)); return sexp;
        }
    }

    public record Parenthesis(SourceRange sourceRange, StmtExpr inner) implements StmtExpr {
        @Override public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
            var sexp = ion.newEmptySexp(); sexp.add(ion.newSymbol("parenthesis"));
            sexp.add(sourceRange.toIon(ion)); sexp.add(inner.toIon(ion)); return sexp;
        }
    }

    public record Assign(SourceRange sourceRange, StmtExpr target, StmtExpr value) implements StmtExpr {
        @Override public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
            var sexp = ion.newEmptySexp(); sexp.add(ion.newSymbol("assign"));
            sexp.add(sourceRange.toIon(ion)); sexp.add(target.toIon(ion)); sexp.add(value.toIon(ion)); return sexp;
        }
    }

    public record Add(SourceRange sourceRange, StmtExpr lhs, StmtExpr rhs) implements StmtExpr {
        @Override public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
            var sexp = ion.newEmptySexp(); sexp.add(ion.newSymbol("add"));
            sexp.add(sourceRange.toIon(ion)); sexp.add(lhs.toIon(ion)); sexp.add(rhs.toIon(ion)); return sexp;
        }
    }

    public record Sub(SourceRange sourceRange, StmtExpr lhs, StmtExpr rhs) implements StmtExpr {
        @Override public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
            var sexp = ion.newEmptySexp(); sexp.add(ion.newSymbol("sub"));
            sexp.add(sourceRange.toIon(ion)); sexp.add(lhs.toIon(ion)); sexp.add(rhs.toIon(ion)); return sexp;
        }
    }

    public record Mul(SourceRange sourceRange, StmtExpr lhs, StmtExpr rhs) implements StmtExpr {
        @Override public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
            var sexp = ion.newEmptySexp(); sexp.add(ion.newSymbol("mul"));
            sexp.add(sourceRange.toIon(ion)); sexp.add(lhs.toIon(ion)); sexp.add(rhs.toIon(ion)); return sexp;
        }
    }

    public record Div(SourceRange sourceRange, StmtExpr lhs, StmtExpr rhs) implements StmtExpr {
        @Override public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
            var sexp = ion.newEmptySexp(); sexp.add(ion.newSymbol("div"));
            sexp.add(sourceRange.toIon(ion)); sexp.add(lhs.toIon(ion)); sexp.add(rhs.toIon(ion)); return sexp;
        }
    }

    public record Mod(SourceRange sourceRange, StmtExpr lhs, StmtExpr rhs) implements StmtExpr {
        @Override public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
            var sexp = ion.newEmptySexp(); sexp.add(ion.newSymbol("mod"));
            sexp.add(sourceRange.toIon(ion)); sexp.add(lhs.toIon(ion)); sexp.add(rhs.toIon(ion)); return sexp;
        }
    }

    public record DivT(SourceRange sourceRange, StmtExpr lhs, StmtExpr rhs) implements StmtExpr {
        @Override public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
            var sexp = ion.newEmptySexp(); sexp.add(ion.newSymbol("divT"));
            sexp.add(sourceRange.toIon(ion)); sexp.add(lhs.toIon(ion)); sexp.add(rhs.toIon(ion)); return sexp;
        }
    }

    public record ModT(SourceRange sourceRange, StmtExpr lhs, StmtExpr rhs) implements StmtExpr {
        @Override public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
            var sexp = ion.newEmptySexp(); sexp.add(ion.newSymbol("modT"));
            sexp.add(sourceRange.toIon(ion)); sexp.add(lhs.toIon(ion)); sexp.add(rhs.toIon(ion)); return sexp;
        }
    }

    public record Eq(SourceRange sourceRange, StmtExpr lhs, StmtExpr rhs) implements StmtExpr {
        @Override public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
            var sexp = ion.newEmptySexp(); sexp.add(ion.newSymbol("eq"));
            sexp.add(sourceRange.toIon(ion)); sexp.add(lhs.toIon(ion)); sexp.add(rhs.toIon(ion)); return sexp;
        }
    }

    public record Neq(SourceRange sourceRange, StmtExpr lhs, StmtExpr rhs) implements StmtExpr {
        @Override public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
            var sexp = ion.newEmptySexp(); sexp.add(ion.newSymbol("neq"));
            sexp.add(sourceRange.toIon(ion)); sexp.add(lhs.toIon(ion)); sexp.add(rhs.toIon(ion)); return sexp;
        }
    }

    public record Gt(SourceRange sourceRange, StmtExpr lhs, StmtExpr rhs) implements StmtExpr {
        @Override public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
            var sexp = ion.newEmptySexp(); sexp.add(ion.newSymbol("gt"));
            sexp.add(sourceRange.toIon(ion)); sexp.add(lhs.toIon(ion)); sexp.add(rhs.toIon(ion)); return sexp;
        }
    }

    public record Lt(SourceRange sourceRange, StmtExpr lhs, StmtExpr rhs) implements StmtExpr {
        @Override public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
            var sexp = ion.newEmptySexp(); sexp.add(ion.newSymbol("lt"));
            sexp.add(sourceRange.toIon(ion)); sexp.add(lhs.toIon(ion)); sexp.add(rhs.toIon(ion)); return sexp;
        }
    }

    public record Le(SourceRange sourceRange, StmtExpr lhs, StmtExpr rhs) implements StmtExpr {
        @Override public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
            var sexp = ion.newEmptySexp(); sexp.add(ion.newSymbol("le"));
            sexp.add(sourceRange.toIon(ion)); sexp.add(lhs.toIon(ion)); sexp.add(rhs.toIon(ion)); return sexp;
        }
    }

    public record Ge(SourceRange sourceRange, StmtExpr lhs, StmtExpr rhs) implements StmtExpr {
        @Override public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
            var sexp = ion.newEmptySexp(); sexp.add(ion.newSymbol("ge"));
            sexp.add(sourceRange.toIon(ion)); sexp.add(lhs.toIon(ion)); sexp.add(rhs.toIon(ion)); return sexp;
        }
    }

    public record And(SourceRange sourceRange, StmtExpr lhs, StmtExpr rhs) implements StmtExpr {
        @Override public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
            var sexp = ion.newEmptySexp(); sexp.add(ion.newSymbol("and"));
            sexp.add(sourceRange.toIon(ion)); sexp.add(lhs.toIon(ion)); sexp.add(rhs.toIon(ion)); return sexp;
        }
    }

    public record Or(SourceRange sourceRange, StmtExpr lhs, StmtExpr rhs) implements StmtExpr {
        @Override public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
            var sexp = ion.newEmptySexp(); sexp.add(ion.newSymbol("or"));
            sexp.add(sourceRange.toIon(ion)); sexp.add(lhs.toIon(ion)); sexp.add(rhs.toIon(ion)); return sexp;
        }
    }

    public record AndThen(SourceRange sourceRange, StmtExpr lhs, StmtExpr rhs) implements StmtExpr {
        @Override public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
            var sexp = ion.newEmptySexp(); sexp.add(ion.newSymbol("andThen"));
            sexp.add(sourceRange.toIon(ion)); sexp.add(lhs.toIon(ion)); sexp.add(rhs.toIon(ion)); return sexp;
        }
    }

    public record OrElse(SourceRange sourceRange, StmtExpr lhs, StmtExpr rhs) implements StmtExpr {
        @Override public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
            var sexp = ion.newEmptySexp(); sexp.add(ion.newSymbol("orElse"));
            sexp.add(sourceRange.toIon(ion)); sexp.add(lhs.toIon(ion)); sexp.add(rhs.toIon(ion)); return sexp;
        }
    }

    public record Implies(SourceRange sourceRange, StmtExpr lhs, StmtExpr rhs) implements StmtExpr {
        @Override public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
            var sexp = ion.newEmptySexp(); sexp.add(ion.newSymbol("implies"));
            sexp.add(sourceRange.toIon(ion)); sexp.add(lhs.toIon(ion)); sexp.add(rhs.toIon(ion)); return sexp;
        }
    }

    public record StrConcat(SourceRange sourceRange, StmtExpr lhs, StmtExpr rhs) implements StmtExpr {
        @Override public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
            var sexp = ion.newEmptySexp(); sexp.add(ion.newSymbol("strConcat"));
            sexp.add(sourceRange.toIon(ion)); sexp.add(lhs.toIon(ion)); sexp.add(rhs.toIon(ion)); return sexp;
        }
    }

    public record Not(SourceRange sourceRange, StmtExpr inner) implements StmtExpr {
        @Override public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
            var sexp = ion.newEmptySexp(); sexp.add(ion.newSymbol("not"));
            sexp.add(sourceRange.toIon(ion)); sexp.add(inner.toIon(ion)); return sexp;
        }
    }

    public record Neg(SourceRange sourceRange, StmtExpr inner) implements StmtExpr {
        @Override public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
            var sexp = ion.newEmptySexp(); sexp.add(ion.newSymbol("neg"));
            sexp.add(sourceRange.toIon(ion)); sexp.add(inner.toIon(ion)); return sexp;
        }
    }

    public record ForallExpr(SourceRange sourceRange, java.lang.String name, LaurelType ty, Trigger trigger, StmtExpr body) implements StmtExpr {
        @Override public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
            var sexp = ion.newEmptySexp(); sexp.add(ion.newSymbol("forallExpr"));
            sexp.add(sourceRange.toIon(ion)); sexp.add(ion.newString(name)); sexp.add(ty.toIon(ion));
            sexp.add(trigger != null ? trigger.toIon(ion) : ion.newNull());
            sexp.add(body.toIon(ion)); return sexp;
        }
    }

    public record ExistsExpr(SourceRange sourceRange, java.lang.String name, LaurelType ty, Trigger trigger, StmtExpr body) implements StmtExpr {
        @Override public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
            var sexp = ion.newEmptySexp(); sexp.add(ion.newSymbol("existsExpr"));
            sexp.add(sourceRange.toIon(ion)); sexp.add(ion.newString(name)); sexp.add(ty.toIon(ion));
            sexp.add(trigger != null ? trigger.toIon(ion) : ion.newNull());
            sexp.add(body.toIon(ion)); return sexp;
        }
    }

    public record IfThenElse(SourceRange sourceRange, StmtExpr cond, StmtExpr thenBranch, ElseBranch elseBranch) implements StmtExpr {
        @Override public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
            var sexp = ion.newEmptySexp(); sexp.add(ion.newSymbol("ifThenElse"));
            sexp.add(sourceRange.toIon(ion)); sexp.add(cond.toIon(ion)); sexp.add(thenBranch.toIon(ion));
            sexp.add(elseBranch != null ? elseBranch.toIon(ion) : ion.newNull());
            return sexp;
        }
    }

    public record Assert(SourceRange sourceRange, StmtExpr cond, ErrorSummary errorMessage) implements StmtExpr {
        @Override public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
            var sexp = ion.newEmptySexp(); sexp.add(ion.newSymbol("assert"));
            sexp.add(sourceRange.toIon(ion)); sexp.add(cond.toIon(ion));
            sexp.add(errorMessage != null ? errorMessage.toIon(ion) : ion.newNull());
            return sexp;
        }
    }

    public record Assume(SourceRange sourceRange, StmtExpr cond) implements StmtExpr {
        @Override public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
            var sexp = ion.newEmptySexp(); sexp.add(ion.newSymbol("assume"));
            sexp.add(sourceRange.toIon(ion)); sexp.add(cond.toIon(ion)); return sexp;
        }
    }

    public record Return(SourceRange sourceRange, StmtExpr value) implements StmtExpr {
        @Override public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
            var sexp = ion.newEmptySexp(); sexp.add(ion.newSymbol("return"));
            sexp.add(sourceRange.toIon(ion)); sexp.add(value.toIon(ion)); return sexp;
        }
    }

    public record Block(SourceRange sourceRange, java.util.List<StmtExpr> stmts) implements StmtExpr {
        @Override public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
            var sexp = ion.newEmptySexp(); sexp.add(ion.newSymbol("block"));
            sexp.add(sourceRange.toIon(ion));
            var _l = ion.newEmptyList(); for (var e : stmts) _l.add(e.toIon(ion));
            sexp.add(_l); return sexp;
        }
    }

    public record LabelledBlock(SourceRange sourceRange, java.util.List<StmtExpr> stmts, java.lang.String label) implements StmtExpr {
        @Override public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
            var sexp = ion.newEmptySexp(); sexp.add(ion.newSymbol("labelledBlock"));
            sexp.add(sourceRange.toIon(ion));
            var _l = ion.newEmptyList(); for (var e : stmts) _l.add(e.toIon(ion));
            sexp.add(_l); sexp.add(ion.newString(label)); return sexp;
        }
    }

    public record Exit(SourceRange sourceRange, java.lang.String label) implements StmtExpr {
        @Override public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
            var sexp = ion.newEmptySexp(); sexp.add(ion.newSymbol("exit"));
            sexp.add(sourceRange.toIon(ion)); sexp.add(ion.newString(label)); return sexp;
        }
    }

    public record While(SourceRange sourceRange, StmtExpr cond, java.util.List<InvariantClause> invariants, StmtExpr body) implements StmtExpr {
        @Override public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
            var sexp = ion.newEmptySexp(); sexp.add(ion.newSymbol("while"));
            sexp.add(sourceRange.toIon(ion)); sexp.add(cond.toIon(ion));
            var _l = ion.newEmptyList(); for (var e : invariants) _l.add(e.toIon(ion));
            sexp.add(_l); sexp.add(body.toIon(ion)); return sexp;
        }
    }

    public record ForLoop(SourceRange sourceRange, StmtExpr init, StmtExpr cond, StmtExpr step, java.util.List<InvariantClause> invariants, StmtExpr body) implements StmtExpr {
        @Override public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
            var sexp = ion.newEmptySexp(); sexp.add(ion.newSymbol("forLoop"));
            sexp.add(sourceRange.toIon(ion)); sexp.add(init.toIon(ion)); sexp.add(cond.toIon(ion)); sexp.add(step.toIon(ion));
            var _l = ion.newEmptyList(); for (var e : invariants) _l.add(e.toIon(ion));
            sexp.add(_l); sexp.add(body.toIon(ion)); return sexp;
        }
    }

    public record IsType(SourceRange sourceRange, StmtExpr target, java.lang.String typeName) implements StmtExpr {
        @Override public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
            var sexp = ion.newEmptySexp(); sexp.add(ion.newSymbol("isType"));
            sexp.add(sourceRange.toIon(ion)); sexp.add(target.toIon(ion)); sexp.add(ion.newString(typeName)); return sexp;
        }
    }

    public record AsType(SourceRange sourceRange, StmtExpr target, java.lang.String typeName) implements StmtExpr {
        @Override public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
            var sexp = ion.newEmptySexp(); sexp.add(ion.newSymbol("asType"));
            sexp.add(sourceRange.toIon(ion)); sexp.add(target.toIon(ion)); sexp.add(ion.newString(typeName)); return sexp;
        }
    }
}
