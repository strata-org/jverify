package org.strata.jverify.laurel;

public sealed interface StmtExpr extends Node permits StmtExpr.LiteralBool, StmtExpr.Int, StmtExpr.Real, StmtExpr.String_, StmtExpr.Hole, StmtExpr.NondetHole, StmtExpr.VarDecl, StmtExpr.Call, StmtExpr.New, StmtExpr.FieldAccess, StmtExpr.Identifier, StmtExpr.Parenthesis, StmtExpr.Assign, StmtExpr.MultiAssign, StmtExpr.Add, StmtExpr.Sub, StmtExpr.Mul, StmtExpr.Div, StmtExpr.Mod, StmtExpr.DivT, StmtExpr.ModT, StmtExpr.Eq, StmtExpr.Neq, StmtExpr.Gt, StmtExpr.Lt, StmtExpr.Le, StmtExpr.Ge, StmtExpr.And, StmtExpr.Or, StmtExpr.AndThen, StmtExpr.OrElse, StmtExpr.Implies, StmtExpr.StrConcat, StmtExpr.Not, StmtExpr.Neg, StmtExpr.PreIncr, StmtExpr.PreDecr, StmtExpr.PostIncr, StmtExpr.PostDecr, StmtExpr.ForallExpr, StmtExpr.ExistsExpr, StmtExpr.IfThenElse, StmtExpr.Assert, StmtExpr.Assume, StmtExpr.Return, StmtExpr.Block, StmtExpr.LabelledBlock, StmtExpr.Exit, StmtExpr.While, StmtExpr.ForLoop, StmtExpr.IsType, StmtExpr.AsType {
    public record LiteralBool(
        SourceRange sourceRange,
        boolean b
    ) implements StmtExpr {
        @Override
        public java.lang.String operationName() { return "Laurel.literalBool"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.literalBool", sourceRange());
            sexp.add($s.serializeBool(b()));
            return sexp;
        }
    }

    public record Int(
        SourceRange sourceRange,
        java.math.BigInteger n
    ) implements StmtExpr {
        @Override
        public java.lang.String operationName() { return "Laurel.int"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.int", sourceRange());
            sexp.add($s.serializeNum(n()));
            return sexp;
        }
    }

    public record Real(
        SourceRange sourceRange,
        java.math.BigDecimal d
    ) implements StmtExpr {
        @Override
        public java.lang.String operationName() { return "Laurel.real"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.real", sourceRange());
            sexp.add($s.serializeDecimal(d()));
            return sexp;
        }
    }

    public record String_(
        SourceRange sourceRange,
        java.lang.String s
    ) implements StmtExpr {
        @Override
        public java.lang.String operationName() { return "Laurel.string"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.string", sourceRange());
            sexp.add($s.serializeStrlit(s()));
            return sexp;
        }
    }

    public record Hole(
        SourceRange sourceRange
    ) implements StmtExpr {
        @Override
        public java.lang.String operationName() { return "Laurel.hole"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.hole", sourceRange());
            return sexp;
        }
    }

    public record NondetHole(
        SourceRange sourceRange
    ) implements StmtExpr {
        @Override
        public java.lang.String operationName() { return "Laurel.nondetHole"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.nondetHole", sourceRange());
            return sexp;
        }
    }

    public record VarDecl(
        SourceRange sourceRange,
        java.lang.String name, java.util.Optional<TypeAnnotation> varType, java.util.Optional<Initializer> assignment
    ) implements StmtExpr {
        @Override
        public java.lang.String operationName() { return "Laurel.varDecl"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.varDecl", sourceRange());
            sexp.add($s.serializeIdent(name()));
            sexp.add($s.serializeOption(varType(), $s::serialize));
            sexp.add($s.serializeOption(assignment(), $s::serialize));
            return sexp;
        }
    }

    public record Call(
        SourceRange sourceRange,
        StmtExpr callee, java.util.List<StmtExpr> args
    ) implements StmtExpr {
        @Override
        public java.lang.String operationName() { return "Laurel.call"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.call", sourceRange());
            sexp.add($s.serialize(callee()));
            sexp.add($s.serializeSeq(args(), "commaSepList", $s::serialize));
            return sexp;
        }
    }

    public record New(
        SourceRange sourceRange,
        java.lang.String name
    ) implements StmtExpr {
        @Override
        public java.lang.String operationName() { return "Laurel.new"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.new", sourceRange());
            sexp.add($s.serializeIdent(name()));
            return sexp;
        }
    }

    public record FieldAccess(
        SourceRange sourceRange,
        StmtExpr obj, java.lang.String field
    ) implements StmtExpr {
        @Override
        public java.lang.String operationName() { return "Laurel.fieldAccess"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.fieldAccess", sourceRange());
            sexp.add($s.serialize(obj()));
            sexp.add($s.serializeIdent(field()));
            return sexp;
        }
    }

    public record Identifier(
        SourceRange sourceRange,
        java.lang.String name
    ) implements StmtExpr {
        @Override
        public java.lang.String operationName() { return "Laurel.identifier"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.identifier", sourceRange());
            sexp.add($s.serializeIdent(name()));
            return sexp;
        }
    }

    public record Parenthesis(
        SourceRange sourceRange,
        StmtExpr inner
    ) implements StmtExpr {
        @Override
        public java.lang.String operationName() { return "Laurel.parenthesis"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.parenthesis", sourceRange());
            sexp.add($s.serialize(inner()));
            return sexp;
        }
    }

    public record Assign(
        SourceRange sourceRange,
        StmtExpr target, StmtExpr value
    ) implements StmtExpr {
        @Override
        public java.lang.String operationName() { return "Laurel.assign"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.assign", sourceRange());
            sexp.add($s.serialize(target()));
            sexp.add($s.serialize(value()));
            return sexp;
        }
    }

    public record MultiAssign(
        SourceRange sourceRange,
        java.util.List<AssignTarget> targets, StmtExpr value
    ) implements StmtExpr {
        @Override
        public java.lang.String operationName() { return "Laurel.multiAssign"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.multiAssign", sourceRange());
            sexp.add($s.serializeSeq(targets(), "commaSepList", $s::serialize));
            sexp.add($s.serialize(value()));
            return sexp;
        }
    }

    public record Add(
        SourceRange sourceRange,
        StmtExpr lhs, StmtExpr rhs
    ) implements StmtExpr {
        @Override
        public java.lang.String operationName() { return "Laurel.add"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.add", sourceRange());
            sexp.add($s.serialize(lhs()));
            sexp.add($s.serialize(rhs()));
            return sexp;
        }
    }

    public record Sub(
        SourceRange sourceRange,
        StmtExpr lhs, StmtExpr rhs
    ) implements StmtExpr {
        @Override
        public java.lang.String operationName() { return "Laurel.sub"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.sub", sourceRange());
            sexp.add($s.serialize(lhs()));
            sexp.add($s.serialize(rhs()));
            return sexp;
        }
    }

    public record Mul(
        SourceRange sourceRange,
        StmtExpr lhs, StmtExpr rhs
    ) implements StmtExpr {
        @Override
        public java.lang.String operationName() { return "Laurel.mul"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.mul", sourceRange());
            sexp.add($s.serialize(lhs()));
            sexp.add($s.serialize(rhs()));
            return sexp;
        }
    }

    public record Div(
        SourceRange sourceRange,
        StmtExpr lhs, StmtExpr rhs
    ) implements StmtExpr {
        @Override
        public java.lang.String operationName() { return "Laurel.div"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.div", sourceRange());
            sexp.add($s.serialize(lhs()));
            sexp.add($s.serialize(rhs()));
            return sexp;
        }
    }

    public record Mod(
        SourceRange sourceRange,
        StmtExpr lhs, StmtExpr rhs
    ) implements StmtExpr {
        @Override
        public java.lang.String operationName() { return "Laurel.mod"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.mod", sourceRange());
            sexp.add($s.serialize(lhs()));
            sexp.add($s.serialize(rhs()));
            return sexp;
        }
    }

    public record DivT(
        SourceRange sourceRange,
        StmtExpr lhs, StmtExpr rhs
    ) implements StmtExpr {
        @Override
        public java.lang.String operationName() { return "Laurel.divT"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.divT", sourceRange());
            sexp.add($s.serialize(lhs()));
            sexp.add($s.serialize(rhs()));
            return sexp;
        }
    }

    public record ModT(
        SourceRange sourceRange,
        StmtExpr lhs, StmtExpr rhs
    ) implements StmtExpr {
        @Override
        public java.lang.String operationName() { return "Laurel.modT"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.modT", sourceRange());
            sexp.add($s.serialize(lhs()));
            sexp.add($s.serialize(rhs()));
            return sexp;
        }
    }

    public record Eq(
        SourceRange sourceRange,
        StmtExpr lhs, StmtExpr rhs
    ) implements StmtExpr {
        @Override
        public java.lang.String operationName() { return "Laurel.eq"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.eq", sourceRange());
            sexp.add($s.serialize(lhs()));
            sexp.add($s.serialize(rhs()));
            return sexp;
        }
    }

    public record Neq(
        SourceRange sourceRange,
        StmtExpr lhs, StmtExpr rhs
    ) implements StmtExpr {
        @Override
        public java.lang.String operationName() { return "Laurel.neq"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.neq", sourceRange());
            sexp.add($s.serialize(lhs()));
            sexp.add($s.serialize(rhs()));
            return sexp;
        }
    }

    public record Gt(
        SourceRange sourceRange,
        StmtExpr lhs, StmtExpr rhs
    ) implements StmtExpr {
        @Override
        public java.lang.String operationName() { return "Laurel.gt"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.gt", sourceRange());
            sexp.add($s.serialize(lhs()));
            sexp.add($s.serialize(rhs()));
            return sexp;
        }
    }

    public record Lt(
        SourceRange sourceRange,
        StmtExpr lhs, StmtExpr rhs
    ) implements StmtExpr {
        @Override
        public java.lang.String operationName() { return "Laurel.lt"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.lt", sourceRange());
            sexp.add($s.serialize(lhs()));
            sexp.add($s.serialize(rhs()));
            return sexp;
        }
    }

    public record Le(
        SourceRange sourceRange,
        StmtExpr lhs, StmtExpr rhs
    ) implements StmtExpr {
        @Override
        public java.lang.String operationName() { return "Laurel.le"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.le", sourceRange());
            sexp.add($s.serialize(lhs()));
            sexp.add($s.serialize(rhs()));
            return sexp;
        }
    }

    public record Ge(
        SourceRange sourceRange,
        StmtExpr lhs, StmtExpr rhs
    ) implements StmtExpr {
        @Override
        public java.lang.String operationName() { return "Laurel.ge"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.ge", sourceRange());
            sexp.add($s.serialize(lhs()));
            sexp.add($s.serialize(rhs()));
            return sexp;
        }
    }

    public record And(
        SourceRange sourceRange,
        StmtExpr lhs, StmtExpr rhs
    ) implements StmtExpr {
        @Override
        public java.lang.String operationName() { return "Laurel.and"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.and", sourceRange());
            sexp.add($s.serialize(lhs()));
            sexp.add($s.serialize(rhs()));
            return sexp;
        }
    }

    public record Or(
        SourceRange sourceRange,
        StmtExpr lhs, StmtExpr rhs
    ) implements StmtExpr {
        @Override
        public java.lang.String operationName() { return "Laurel.or"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.or", sourceRange());
            sexp.add($s.serialize(lhs()));
            sexp.add($s.serialize(rhs()));
            return sexp;
        }
    }

    public record AndThen(
        SourceRange sourceRange,
        StmtExpr lhs, StmtExpr rhs
    ) implements StmtExpr {
        @Override
        public java.lang.String operationName() { return "Laurel.andThen"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.andThen", sourceRange());
            sexp.add($s.serialize(lhs()));
            sexp.add($s.serialize(rhs()));
            return sexp;
        }
    }

    public record OrElse(
        SourceRange sourceRange,
        StmtExpr lhs, StmtExpr rhs
    ) implements StmtExpr {
        @Override
        public java.lang.String operationName() { return "Laurel.orElse"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.orElse", sourceRange());
            sexp.add($s.serialize(lhs()));
            sexp.add($s.serialize(rhs()));
            return sexp;
        }
    }

    public record Implies(
        SourceRange sourceRange,
        StmtExpr lhs, StmtExpr rhs
    ) implements StmtExpr {
        @Override
        public java.lang.String operationName() { return "Laurel.implies"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.implies", sourceRange());
            sexp.add($s.serialize(lhs()));
            sexp.add($s.serialize(rhs()));
            return sexp;
        }
    }

    public record StrConcat(
        SourceRange sourceRange,
        StmtExpr lhs, StmtExpr rhs
    ) implements StmtExpr {
        @Override
        public java.lang.String operationName() { return "Laurel.strConcat"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.strConcat", sourceRange());
            sexp.add($s.serialize(lhs()));
            sexp.add($s.serialize(rhs()));
            return sexp;
        }
    }

    public record Not(
        SourceRange sourceRange,
        StmtExpr inner
    ) implements StmtExpr {
        @Override
        public java.lang.String operationName() { return "Laurel.not"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.not", sourceRange());
            sexp.add($s.serialize(inner()));
            return sexp;
        }
    }

    public record Neg(
        SourceRange sourceRange,
        StmtExpr inner
    ) implements StmtExpr {
        @Override
        public java.lang.String operationName() { return "Laurel.neg"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.neg", sourceRange());
            sexp.add($s.serialize(inner()));
            return sexp;
        }
    }

    public record PreIncr(
        SourceRange sourceRange,
        StmtExpr target
    ) implements StmtExpr {
        @Override
        public java.lang.String operationName() { return "Laurel.preIncr"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.preIncr", sourceRange());
            sexp.add($s.serialize(target()));
            return sexp;
        }
    }

    public record PreDecr(
        SourceRange sourceRange,
        StmtExpr target
    ) implements StmtExpr {
        @Override
        public java.lang.String operationName() { return "Laurel.preDecr"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.preDecr", sourceRange());
            sexp.add($s.serialize(target()));
            return sexp;
        }
    }

    public record PostIncr(
        SourceRange sourceRange,
        StmtExpr target
    ) implements StmtExpr {
        @Override
        public java.lang.String operationName() { return "Laurel.postIncr"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.postIncr", sourceRange());
            sexp.add($s.serialize(target()));
            return sexp;
        }
    }

    public record PostDecr(
        SourceRange sourceRange,
        StmtExpr target
    ) implements StmtExpr {
        @Override
        public java.lang.String operationName() { return "Laurel.postDecr"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.postDecr", sourceRange());
            sexp.add($s.serialize(target()));
            return sexp;
        }
    }

    public record ForallExpr(
        SourceRange sourceRange,
        java.lang.String name, LaurelType ty, java.util.Optional<Trigger> trigger, StmtExpr body
    ) implements StmtExpr {
        @Override
        public java.lang.String operationName() { return "Laurel.forallExpr"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.forallExpr", sourceRange());
            sexp.add($s.serializeIdent(name()));
            sexp.add($s.serialize(ty()));
            sexp.add($s.serializeOption(trigger(), $s::serialize));
            sexp.add($s.serialize(body()));
            return sexp;
        }
    }

    public record ExistsExpr(
        SourceRange sourceRange,
        java.lang.String name, LaurelType ty, java.util.Optional<Trigger> trigger, StmtExpr body
    ) implements StmtExpr {
        @Override
        public java.lang.String operationName() { return "Laurel.existsExpr"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.existsExpr", sourceRange());
            sexp.add($s.serializeIdent(name()));
            sexp.add($s.serialize(ty()));
            sexp.add($s.serializeOption(trigger(), $s::serialize));
            sexp.add($s.serialize(body()));
            return sexp;
        }
    }

    public record IfThenElse(
        SourceRange sourceRange,
        StmtExpr cond, StmtExpr thenBranch, java.util.Optional<ElseBranch> elseBranch
    ) implements StmtExpr {
        @Override
        public java.lang.String operationName() { return "Laurel.ifThenElse"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.ifThenElse", sourceRange());
            sexp.add($s.serialize(cond()));
            sexp.add($s.serialize(thenBranch()));
            sexp.add($s.serializeOption(elseBranch(), $s::serialize));
            return sexp;
        }
    }

    public record Assert(
        SourceRange sourceRange,
        StmtExpr cond, java.util.Optional<ErrorSummary> errorMessage
    ) implements StmtExpr {
        @Override
        public java.lang.String operationName() { return "Laurel.assert"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.assert", sourceRange());
            sexp.add($s.serialize(cond()));
            sexp.add($s.serializeOption(errorMessage(), $s::serialize));
            return sexp;
        }
    }

    public record Assume(
        SourceRange sourceRange,
        StmtExpr cond
    ) implements StmtExpr {
        @Override
        public java.lang.String operationName() { return "Laurel.assume"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.assume", sourceRange());
            sexp.add($s.serialize(cond()));
            return sexp;
        }
    }

    public record Return(
        SourceRange sourceRange,
        StmtExpr value
    ) implements StmtExpr {
        @Override
        public java.lang.String operationName() { return "Laurel.return"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.return", sourceRange());
            sexp.add($s.serialize(value()));
            return sexp;
        }
    }

    public record Block(
        SourceRange sourceRange,
        java.util.List<StmtExpr> stmts
    ) implements StmtExpr {
        @Override
        public java.lang.String operationName() { return "Laurel.block"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.block", sourceRange());
            sexp.add($s.serializeSeq(stmts(), "semicolonSepList", $s::serialize));
            return sexp;
        }
    }

    public record LabelledBlock(
        SourceRange sourceRange,
        java.util.List<StmtExpr> stmts, java.lang.String label
    ) implements StmtExpr {
        @Override
        public java.lang.String operationName() { return "Laurel.labelledBlock"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.labelledBlock", sourceRange());
            sexp.add($s.serializeSeq(stmts(), "semicolonSepList", $s::serialize));
            sexp.add($s.serializeIdent(label()));
            return sexp;
        }
    }

    public record Exit(
        SourceRange sourceRange,
        java.lang.String label
    ) implements StmtExpr {
        @Override
        public java.lang.String operationName() { return "Laurel.exit"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.exit", sourceRange());
            sexp.add($s.serializeIdent(label()));
            return sexp;
        }
    }

    public record While(
        SourceRange sourceRange,
        StmtExpr cond, java.util.List<InvariantClause> invariants, StmtExpr body
    ) implements StmtExpr {
        @Override
        public java.lang.String operationName() { return "Laurel.while"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.while", sourceRange());
            sexp.add($s.serialize(cond()));
            sexp.add($s.serializeSeq(invariants(), "seq", $s::serialize));
            sexp.add($s.serialize(body()));
            return sexp;
        }
    }

    public record ForLoop(
        SourceRange sourceRange,
        StmtExpr init, StmtExpr cond, StmtExpr step, java.util.List<InvariantClause> invariants, StmtExpr body
    ) implements StmtExpr {
        @Override
        public java.lang.String operationName() { return "Laurel.forLoop"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.forLoop", sourceRange());
            sexp.add($s.serialize(init()));
            sexp.add($s.serialize(cond()));
            sexp.add($s.serialize(step()));
            sexp.add($s.serializeSeq(invariants(), "seq", $s::serialize));
            sexp.add($s.serialize(body()));
            return sexp;
        }
    }

    public record IsType(
        SourceRange sourceRange,
        StmtExpr target, java.lang.String typeName
    ) implements StmtExpr {
        @Override
        public java.lang.String operationName() { return "Laurel.isType"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.isType", sourceRange());
            sexp.add($s.serialize(target()));
            sexp.add($s.serializeIdent(typeName()));
            return sexp;
        }
    }

    public record AsType(
        SourceRange sourceRange,
        StmtExpr target, java.lang.String typeName
    ) implements StmtExpr {
        @Override
        public java.lang.String operationName() { return "Laurel.asType"; }

        @Override
        public com.amazon.ion.IonSexp toIon(IonSerializer $s) {
            var sexp = $s.newOp("Laurel.asType", sourceRange());
            sexp.add($s.serialize(target()));
            sexp.add($s.serializeIdent(typeName()));
            return sexp;
        }
    }
}
