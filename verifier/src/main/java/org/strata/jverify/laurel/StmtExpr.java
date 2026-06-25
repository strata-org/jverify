package org.strata.jverify.laurel;

public sealed interface StmtExpr permits StmtExpr.IfThenElse, StmtExpr.Block, StmtExpr.While, StmtExpr.Exit, StmtExpr.Return, StmtExpr.LiteralInt, StmtExpr.LiteralBool, StmtExpr.LiteralString, StmtExpr.LiteralDecimal, StmtExpr.LiteralBv, StmtExpr.Var, StmtExpr.Assign, StmtExpr.IncrDecr, StmtExpr.PureFieldUpdate, StmtExpr.StaticCall, StmtExpr.PrimitiveOp, StmtExpr.New, StmtExpr.This, StmtExpr.ReferenceEquals, StmtExpr.AsType, StmtExpr.IsType, StmtExpr.InstanceCall, StmtExpr.Quantifier, StmtExpr.Assigned, StmtExpr.Old, StmtExpr.Fresh, StmtExpr.Assert, StmtExpr.Assume, StmtExpr.ProveBy, StmtExpr.ContractOf, StmtExpr.Abstract, StmtExpr.All, StmtExpr.Hole {
    com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion);

    public record IfThenElse(AstNode cond, AstNode thenBranch, AstNode elseBranch) implements StmtExpr {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("IfThenElse"));
        sexp.add(cond().toIon(ion));
        sexp.add(thenBranch().toIon(ion));
        sexp.add((elseBranch() != null ? elseBranch().toIon(ion) : ion.newNull()));
        return sexp;
        }
    }

    public record Block(java.util.List<AstNode> statements, java.lang.String label) implements StmtExpr {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("Block"));
        var _l0 = ion.newEmptyList();
        for (var e : statements()) _l0.add(e.toIon(ion));
        sexp.add(_l0);
        sexp.add((label() != null ? ion.newString(label()) : ion.newNull()));
        return sexp;
        }
    }

    public record While(AstNode cond, java.util.List<AstNode> invariants, AstNode decreases, AstNode body, java.lang.Object postTest) implements StmtExpr {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("While"));
        sexp.add(cond().toIon(ion));
        var _l1 = ion.newEmptyList();
        for (var e : invariants()) _l1.add(e.toIon(ion));
        sexp.add(_l1);
        sexp.add((decreases() != null ? decreases().toIon(ion) : ion.newNull()));
        sexp.add(body().toIon(ion));
        sexp.add(ion.newNull());
        return sexp;
        }
    }

    public record Exit(java.lang.String target) implements StmtExpr {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("Exit"));
        sexp.add(ion.newString(target()));
        return sexp;
        }
    }

    public record Return(AstNode value) implements StmtExpr {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("Return"));
        sexp.add((value() != null ? value().toIon(ion) : ion.newNull()));
        return sexp;
        }
    }

    public record LiteralInt(long value) implements StmtExpr {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("LiteralInt"));
        sexp.add(ion.newInt(value()));
        return sexp;
        }
    }

    public record LiteralBool(boolean value) implements StmtExpr {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("LiteralBool"));
        sexp.add(ion.newBool(value()));
        return sexp;
        }
    }

    public record LiteralString(java.lang.String value) implements StmtExpr {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("LiteralString"));
        sexp.add(ion.newString(value()));
        return sexp;
        }
    }

    public record LiteralDecimal(java.math.BigDecimal value) implements StmtExpr {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("LiteralDecimal"));
        sexp.add(ion.newDecimal(value()));
        return sexp;
        }
    }

    public record LiteralBv(long value, long width) implements StmtExpr {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("LiteralBv"));
        sexp.add(ion.newInt(value()));
        sexp.add(ion.newInt(width()));
        return sexp;
        }
    }

    public record Var(Variable var_) implements StmtExpr {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("Var"));
        sexp.add(var_().toIon(ion));
        return sexp;
        }
    }

    public record Assign(java.util.List<AstNode> targets, AstNode value) implements StmtExpr {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("Assign"));
        var _l0 = ion.newEmptyList();
        for (var e : targets()) _l0.add(e.toIon(ion));
        sexp.add(_l0);
        sexp.add(value().toIon(ion));
        return sexp;
        }
    }

    public record IncrDecr(IncrDecrMode mode, IncrDecrOp op, AstNode target) implements StmtExpr {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("IncrDecr"));
        sexp.add(mode().toIon(ion));
        sexp.add(op().toIon(ion));
        sexp.add(target().toIon(ion));
        return sexp;
        }
    }

    public record PureFieldUpdate(AstNode target, Identifier fieldName, AstNode newValue) implements StmtExpr {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("PureFieldUpdate"));
        sexp.add(target().toIon(ion));
        sexp.add(fieldName().toIon(ion));
        sexp.add(newValue().toIon(ion));
        return sexp;
        }
    }

    public record StaticCall(Identifier callee, java.util.List<AstNode> arguments) implements StmtExpr {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("StaticCall"));
        sexp.add(callee().toIon(ion));
        var _l1 = ion.newEmptyList();
        for (var e : arguments()) _l1.add(e.toIon(ion));
        sexp.add(_l1);
        return sexp;
        }
    }

    public record PrimitiveOp(Operation operator, java.util.List<AstNode> arguments, java.lang.Object skipProof) implements StmtExpr {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("PrimitiveOp"));
        sexp.add(operator().toIon(ion));
        var _l1 = ion.newEmptyList();
        for (var e : arguments()) _l1.add(e.toIon(ion));
        sexp.add(_l1);
        sexp.add(ion.newNull());
        return sexp;
        }
    }

    public record New(Identifier ref) implements StmtExpr {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("New"));
        sexp.add(ref().toIon(ion));
        return sexp;
        }
    }

    public record This() implements StmtExpr {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("This"));

        return sexp;
        }
    }

    public record ReferenceEquals(AstNode lhs, AstNode rhs) implements StmtExpr {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("ReferenceEquals"));
        sexp.add(lhs().toIon(ion));
        sexp.add(rhs().toIon(ion));
        return sexp;
        }
    }

    public record AsType(AstNode target, AstNode targetType) implements StmtExpr {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("AsType"));
        sexp.add(target().toIon(ion));
        sexp.add(targetType().toIon(ion));
        return sexp;
        }
    }

    public record IsType(AstNode target, AstNode type) implements StmtExpr {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("IsType"));
        sexp.add(target().toIon(ion));
        sexp.add(type().toIon(ion));
        return sexp;
        }
    }

    public record InstanceCall(AstNode target, Identifier callee, java.util.List<AstNode> arguments) implements StmtExpr {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("InstanceCall"));
        sexp.add(target().toIon(ion));
        sexp.add(callee().toIon(ion));
        var _l2 = ion.newEmptyList();
        for (var e : arguments()) _l2.add(e.toIon(ion));
        sexp.add(_l2);
        return sexp;
        }
    }

    public record Quantifier(QuantifierMode mode, Parameter param, AstNode trigger, AstNode body) implements StmtExpr {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("Quantifier"));
        sexp.add(mode().toIon(ion));
        sexp.add(param().toIon(ion));
        sexp.add((trigger() != null ? trigger().toIon(ion) : ion.newNull()));
        sexp.add(body().toIon(ion));
        return sexp;
        }
    }

    public record Assigned(AstNode name) implements StmtExpr {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("Assigned"));
        sexp.add(name().toIon(ion));
        return sexp;
        }
    }

    public record Old(AstNode value) implements StmtExpr {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("Old"));
        sexp.add(value().toIon(ion));
        return sexp;
        }
    }

    public record Fresh(AstNode value) implements StmtExpr {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("Fresh"));
        sexp.add(value().toIon(ion));
        return sexp;
        }
    }

    public record Assert(Condition condition) implements StmtExpr {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("Assert"));
        sexp.add(condition().toIon(ion));
        return sexp;
        }
    }

    public record Assume(AstNode condition) implements StmtExpr {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("Assume"));
        sexp.add(condition().toIon(ion));
        return sexp;
        }
    }

    public record ProveBy(AstNode value, AstNode proof) implements StmtExpr {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("ProveBy"));
        sexp.add(value().toIon(ion));
        sexp.add(proof().toIon(ion));
        return sexp;
        }
    }

    public record ContractOf(ContractType type, AstNode function) implements StmtExpr {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("ContractOf"));
        sexp.add(type().toIon(ion));
        sexp.add(function().toIon(ion));
        return sexp;
        }
    }

    public record Abstract() implements StmtExpr {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("Abstract"));

        return sexp;
        }
    }

    public record All() implements StmtExpr {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("All"));

        return sexp;
        }
    }

    public record Hole(java.lang.Object deterministic, java.lang.Object type) implements StmtExpr {
        @Override
        public com.amazon.ion.IonValue toIon(com.amazon.ion.IonSystem ion) {
        var sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("Hole"));
        sexp.add(ion.newNull());
        sexp.add(ion.newNull());
        return sexp;
        }
    }
}
