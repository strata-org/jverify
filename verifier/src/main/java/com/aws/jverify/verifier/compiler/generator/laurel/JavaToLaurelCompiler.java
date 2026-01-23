package com.aws.jverify.verifier.compiler.generator.laurel;

import com.aws.jverify.Nullable;
import com.aws.jverify.common.Position;
import com.aws.jverify.laurel.*;
import com.aws.jverify.verifier.VerifierOptions;
import com.aws.jverify.verifier.compiler.JavaViolationException;
import com.aws.jverify.verifier.compiler.frontend.JavaLowerer;
import com.aws.jverify.verifier.compiler.simplifications.JVerifyUtils;
import com.aws.jverify.verifier.compiler.simplifications.MethodOrLoopContractCompiler;
import com.aws.jverify.verifier.laurel.FilesMap;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.TypeTag;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.tree.TreeScanner;
import com.sun.tools.javac.util.Context;

import javax.tools.JavaFileObject;
import java.net.URI;
import java.util.*;

public class JavaToLaurelCompiler {
    private final JavaLowerer lowerer;
    private final Context context;
    private final MethodOrLoopContractCompiler contractCompiler;
    JCTree.JCCompilationUnit currentCompilationUnit;

    public JavaToLaurelCompiler(Context context) {
        this.context = context;
        lowerer = context.get(JavaLowerer.class);
        contractCompiler = MethodOrLoopContractCompiler.instance(context);
    }

    public record AnalysisResult(List<LaurelFile> files, FilesMap filesMap) {}

    public AnalysisResult analyzeJavaCode(VerifierOptions verifierOptions, List<JavaFileObject> readFiles) {
        var result = new ArrayList<LaurelFile>();
        var loweredResult = lowerer.lowerJava(verifierOptions, readFiles);

        Map<URI, com.sun.tools.javac.util.Position.LineMap> lineMaps = new HashMap<>();
        boolean first = true;
        for (var compilationUnit : loweredResult.parsed()) {
            // Skip contract/library sources (like object-contract.java)
            if (lowerer.isContractSource(compilationUnit)) {
                continue;
            }
            currentCompilationUnit = compilationUnit;
            var visitor = new StaticMethodCollector();
            compilationUnit.accept(visitor);

            List<TopLevel> topLevels = new ArrayList<>(first ? getPredefinedTypes() : List.of());
            first = false;
            topLevels.addAll(visitor.procedures.stream()
                    .map(p -> (TopLevel) new TopLevelProcedure(toSourceRange(compilationUnit), p))
                    .toList());
            result.add(new LaurelFile(compilationUnit.sourcefile.toUri(),
                    new Program(toSourceRange(compilationUnit), topLevels)));
            lineMaps.put(compilationUnit.sourcefile.toUri(), compilationUnit.getLineMap());
        }

        FilesMap filesMap = (uri, offset) -> {
            var lineMap = lineMaps.get(uri);
            if (lineMap == null) throw new RuntimeException("Could not find line map for " + uri);
            long line = lineMap.getLineNumber(offset);
            long lineStart = lineMap.getStartPosition(line);
            return new Position((int)line, (int)(offset - lineStart) + 1);
        };

        return new AnalysisResult(result, filesMap);
    }

    SourceRange toSourceRange(JCTree node) {
        int startPos = TreeInfo.getStartPos(node);
        int endPos = TreeInfo.getEndPos(node, currentCompilationUnit.endPositions);
        SourceRange sourceRange = new SourceRange(startPos, endPos == -1 ? startPos : endPos);
        if (sourceRange.start() == -1 || sourceRange.stop() == -1 ) {
            return new SourceRange(0,0);
        }
        return sourceRange;
    }

    private List<TopLevel> getPredefinedTypes() {
        return List.of(
            constrainedType("int32", -2147483648L, 2147483647L),
            constrainedType("nat", 0L, null)
        );
    }

    private TopLevel constrainedType(String name, long min, Long max) {
        var x = new Identifier(toSourceRange(currentCompilationUnit), "x");
        StmtExpr constraint;
        if (max == null) {
            constraint = new Ge(toSourceRange(currentCompilationUnit), x, Laurel.int_(toSourceRange(currentCompilationUnit), min));
        } else {
            constraint = new And(toSourceRange(currentCompilationUnit),
                new Ge(toSourceRange(currentCompilationUnit), x, min >= 0 ? 
                        Laurel.int_(toSourceRange(currentCompilationUnit), min) : new Neg(toSourceRange(currentCompilationUnit), Laurel.int_(toSourceRange(currentCompilationUnit), -min))),
                new Le(toSourceRange(currentCompilationUnit), x, Laurel.int_(toSourceRange(currentCompilationUnit), max)));
        }
        return new TopLevelConstrainedType(toSourceRange(currentCompilationUnit),
            new ConstrainedType_(toSourceRange(currentCompilationUnit), name, "x", new IntType(toSourceRange(currentCompilationUnit)), constraint, Laurel.int_(toSourceRange(currentCompilationUnit), 0)));
    }

    private record Contracts(List<RequiresClause> requires, List<EnsuresClause> ensures,
                            List<JCTree.JCStatement> bodyStatements) {}

    private class StaticMethodCollector extends TreeScanner {
        final List<Procedure> procedures = new ArrayList<>();

        @Override
        public void visitMethodDef(JCTree.JCMethodDecl method) {
            if ((method.mods.flags & Flags.STATIC) != 0) {
                try {
                    String methodName = method.name.toString();

                    List<Parameter> params = new ArrayList<>();
                    for (var param : method.params) {
                        params.add(new Parameter_(toSourceRange(param), param.name.toString(), translateType(param.vartype)));
                    }

                    Optional<OptionalReturnType> returnType = Optional.empty();
                    if (method.restype != null && !isVoid(method.restype)) {
                        returnType = Optional.of(new OptionalReturnType_(toSourceRange(method.restype), translateType(method.restype)));
                    }

                    var contracts = extractContracts(method.body);
                    StmtExpr body = convertMethodBody(contracts.bodyStatements);

                    procedures.add(new Procedure_(toSourceRange(method), methodName, params, returnType,
                            Optional.empty(), contracts.requires, contracts.ensures, body));
                } catch (JavaViolationException e) {
                    System.err.println("Skipping method " + method.sym.owner + "." + method.name + ": " + e.getMessage());
                }
            }
            super.visitMethodDef(method);
        }

        private boolean isVoid(JCTree.JCExpression type) {
            return type instanceof JCTree.JCPrimitiveTypeTree prim && prim.typetag == TypeTag.VOID;
        }

        private LaurelType translateType(JCTree tree) {
            if (tree instanceof JCTree.JCPrimitiveTypeTree prim) {
                return switch (prim.typetag) {
                    case INT -> new CompositeType(toSourceRange(prim), "int32");
                    case LONG -> new IntType(toSourceRange(prim));
                    case BOOLEAN -> new BoolType(toSourceRange(prim));
                    default -> throw new JavaViolationException("Unsupported primitive type: " + prim.typetag);
                };
            } else if (tree instanceof JCTree.JCArrayTypeTree arr) {
                return new ArrayType(toSourceRange(arr), translateType(arr.elemtype));
            } else if (tree instanceof JCTree.JCIdent ident) {
                return new CompositeType(toSourceRange(ident), ident.name.toString());
            } else if (tree instanceof JCTree.JCTypeApply typeApply) {
                // Generic type like JArray<T> - just use the base type name for now
                if (typeApply.clazz instanceof JCTree.JCIdent ident) {
                    return new CompositeType(toSourceRange(typeApply), ident.name.toString());
                }
            }
            throw new JavaViolationException("Unsupported type: " + tree.getClass().getName());
        }

        private Contracts extractContracts(JCTree.JCBlock block) {
            if (block == null) return new Contracts(List.of(), List.of(), List.of());

            var contract = contractCompiler.getContract(block);
            var bodyStatements = MethodOrLoopContractCompiler.getImplementationStatements(block);

            List<RequiresClause> requires = new ArrayList<>();
            List<EnsuresClause> ensures = new ArrayList<>();

            for (var pre : contract.preconditions()) {
                requires.add(new RequiresClause_(toSourceRange(pre.get()), convertExpression(pre.get())));
            }
            for (var post : contract.postconditions()) {
                ensures.add(new EnsuresClause_(toSourceRange(post.get()), convertPostcondition(post.get())));
            }

            return new Contracts(requires, ensures, new ArrayList<>(bodyStatements));
        }

        private StmtExpr convertPostcondition(JCTree.JCExpression arg) {
            if (arg instanceof JCTree.JCLambda lambda && lambda.params.size() == 1) {
                var paramName = lambda.params.getFirst().name.toString();
                var body = convertExpression((JCTree.JCExpression) lambda.body);
                return substituteIdent(body, paramName, "result");
            }
            return convertExpression(arg);
        }

        private StmtExpr substituteIdent(StmtExpr expr, String from, String to) {
            return switch (expr) {
                case Identifier id -> id.name().equals(from) ? new Identifier(id.sourceRange(), to) : id;
                case Add op -> new Add(op.sourceRange(), sub(op.lhs(), from, to), sub(op.rhs(), from, to));
                case Sub op -> new Sub(op.sourceRange(), sub(op.lhs(), from, to), sub(op.rhs(), from, to));
                case Mul op -> new Mul(op.sourceRange(), sub(op.lhs(), from, to), sub(op.rhs(), from, to));
                case Div op -> new Div(op.sourceRange(), sub(op.lhs(), from, to), sub(op.rhs(), from, to));
                case Mod op -> new Mod(op.sourceRange(), sub(op.lhs(), from, to), sub(op.rhs(), from, to));
                case Eq op -> new Eq(op.sourceRange(), sub(op.lhs(), from, to), sub(op.rhs(), from, to));
                case Neq op -> new Neq(op.sourceRange(), sub(op.lhs(), from, to), sub(op.rhs(), from, to));
                case Lt op -> new Lt(op.sourceRange(), sub(op.lhs(), from, to), sub(op.rhs(), from, to));
                case Le op -> new Le(op.sourceRange(), sub(op.lhs(), from, to), sub(op.rhs(), from, to));
                case Gt op -> new Gt(op.sourceRange(), sub(op.lhs(), from, to), sub(op.rhs(), from, to));
                case Ge op -> new Ge(op.sourceRange(), sub(op.lhs(), from, to), sub(op.rhs(), from, to));
                case And op -> new And(op.sourceRange(), sub(op.lhs(), from, to), sub(op.rhs(), from, to));
                case Or op -> new Or(op.sourceRange(), sub(op.lhs(), from, to), sub(op.rhs(), from, to));
                case Implies op -> new Implies(op.sourceRange(), sub(op.lhs(), from, to), sub(op.rhs(), from, to));
                case Not op -> new Not(op.sourceRange(), sub(op.inner(), from, to));
                case Neg op -> new Neg(op.sourceRange(), sub(op.inner(), from, to));
                case ArrayIndex ai -> new ArrayIndex(ai.sourceRange(), sub(ai.arr(), from, to), sub(ai.idx(), from, to));
                case FieldAccess fa -> new FieldAccess(fa.sourceRange(), sub(fa.obj(), from, to), fa.field());
                case Call c -> new Call(c.sourceRange(), sub(c.callee(), from, to), c.args().stream().map(a -> sub(a, from, to)).toList());
                default -> expr;
            };
        }

        private StmtExpr sub(StmtExpr e, String from, String to) { return substituteIdent(e, from, to); }

        private StmtExpr convertMethodBody(List<JCTree.JCStatement> statements) {
            if (statements.isEmpty()) return new Block(toSourceRange(currentCompilationUnit), List.of());
            List<StmtExpr> stmts = new ArrayList<>();
            for (var statement : statements) {
                StmtExpr converted = convertStatement(statement);
                if (converted != null) stmts.add(converted);
            }
            return new Block(toSourceRange(statements.getFirst()), stmts);
        }

        private StmtExpr convertStatement(JCTree.JCStatement statement) {
            if (statement instanceof JCTree.JCAssert assertStmt) {
                return new Assert(toSourceRange(assertStmt), convertExpression(assertStmt.cond));
            } else if (statement instanceof JCTree.JCExpressionStatement exprStmt) {
                return convertExpressionStatement(exprStmt);
            } else if (statement instanceof JCTree.JCBlock block) {
                List<StmtExpr> stmts = new ArrayList<>();
                for (var s : block.stats) {
                    var converted = convertStatement(s);
                    if (converted != null) stmts.add(converted);
                }
                return new Block(toSourceRange(block), stmts);
            } else if (statement instanceof JCTree.JCReturn ret) {
                return new Return(toSourceRange(ret), ret.expr != null ? convertExpression(ret.expr) : new LiteralBool(toSourceRange(ret), true));
            } else if (statement instanceof JCTree.JCVariableDecl varDecl) {
                Optional<OptionalAssignment> init = varDecl.init != null ? Optional.of(new OptionalAssignment_(toSourceRange(varDecl.init), convertExpression(varDecl.init)))
                                                : Optional.empty();
                return new VarDecl(toSourceRange(varDecl), varDecl.name.toString(),
                    Optional.of(new OptionalType_(toSourceRange(varDecl.vartype), translateType(varDecl.vartype))), init);
            } else if (statement instanceof JCTree.JCIf ifStmt) {
                Optional<OptionalElse> elseBranch = ifStmt.elsepart != null
                    ? Optional.of(new OptionalElse_(toSourceRange(ifStmt.elsepart), convertStatement(ifStmt.elsepart)))
                    : Optional.empty();
                return new IfThenElse(toSourceRange(ifStmt), convertExpression(ifStmt.cond), convertStatement(ifStmt.thenpart), elseBranch);
            } else if (statement instanceof JCTree.JCWhileLoop whileLoop) {
                var contract = contractCompiler.getContract(whileLoop.body);
                List<InvariantClause> invariants = new ArrayList<>();
                for (var inv : contract.loopInvariants()) {
                    invariants.add(new InvariantClause_(toSourceRange(inv.get()), convertExpression(inv.get())));
                }
                var bodyStmts = MethodOrLoopContractCompiler.getImplementationStatements(whileLoop.body);
                return new While(toSourceRange(whileLoop), convertExpression(whileLoop.cond), invariants, convertMethodBody(new ArrayList<>(bodyStmts)));
            }
            throw new JavaViolationException("Unsupported statement: " + statement.getClass().getName());
        }

        private StmtExpr convertExpressionStatement(JCTree.JCExpressionStatement exprStmt) {
            var expr = exprStmt.expr;
            if (expr instanceof JCTree.JCMethodInvocation inv) {
                var jverifyMethod = JVerifyUtils.getJVerifyMethod(inv);
                if (jverifyMethod != null) {
                    var name = jverifyMethod.getQualifiedName().toString();
                    if (name.equals("invariant")) return null;
                }
            }
            return convertExpression(expr);
        }

        private StmtExpr convertExpression(JCTree.JCExpression expr) {
            if (expr instanceof JCTree.JCLiteral literal) {
                return convertLiteral(literal);
            } else if (expr instanceof JCTree.JCIdent ident) {
                return new Identifier(toSourceRange(ident), ident.name.toString());
            } else if (expr instanceof JCTree.JCBinary binary) {
                return convertBinary(binary);
            } else if (expr instanceof JCTree.JCUnary unary) {
                return convertUnary(unary);
            } else if (expr instanceof JCTree.JCParens parens) {
                // Skip explicit parentheses - formatter will add them based on precedence
                return convertExpression(parens.expr);
            } else if (expr instanceof JCTree.JCAssign assign) {
                return new Assign(toSourceRange(assign), convertExpression(assign.lhs), convertExpression(assign.rhs));
            } else if (expr instanceof JCTree.JCArrayAccess arr) {
                return new ArrayIndex(toSourceRange(arr), convertExpression(arr.indexed), convertExpression(arr.index));
            } else if (expr instanceof JCTree.JCFieldAccess field) {
                if (field.name.toString().equals("length") &&
                    field.selected.type != null &&
                    field.selected.type.getKind() == javax.lang.model.type.TypeKind.ARRAY) {
                    return staticCall(toSourceRange(field), "Array.Length", convertExpression(field.selected));
                }
                // Inline known static constants
                if (field.sym instanceof Symbol.VarSymbol vs && vs.isStatic() && vs.getConstValue() != null) {
                    Object val = vs.getConstValue();
                    if (val instanceof Number n) {
                        long v = n.longValue();
                        return v >= 0 ? Laurel.int_(toSourceRange(field), v) : new Neg(toSourceRange(field), 
                                Laurel.int_(toSourceRange(field), -v));
                    }
                }
                return new FieldAccess(toSourceRange(field), convertExpression(field.selected), field.name.toString());
            } else if (expr instanceof JCTree.JCMethodInvocation inv) {
                return convertMethodInvocation(inv);
            } else if (expr instanceof JCTree.JCConditional cond) {
                return new IfThenElse(toSourceRange(cond), convertExpression(cond.cond), convertExpression(cond.truepart),
                    Optional.of(new OptionalElse_(toSourceRange(cond.falsepart), convertExpression(cond.falsepart))));
            }
            throw new JavaViolationException("Unsupported expression: " + expr.getClass().getName());
        }

        private StmtExpr convertLiteral(JCTree.JCLiteral literal) {
            return switch (literal.typetag) {
                case BOOLEAN -> new LiteralBool(toSourceRange(literal), (int)literal.value != 0);
                case INT, LONG -> {
                    long val = ((Number)literal.value).longValue();
                    yield val >= 0 ? Laurel.int_(toSourceRange(literal), val) : new Neg(toSourceRange(literal), 
                            Laurel.int_(toSourceRange(literal), -val));
                }
                default -> throw new JavaViolationException("Unsupported literal type: " + literal.typetag);
            };
        }

        private StmtExpr convertBinary(JCTree.JCBinary binary) {
            var lhs = convertExpression(binary.lhs);
            var rhs = convertExpression(binary.rhs);
            return switch (binary.getTag()) {
                case PLUS -> new Add(toSourceRange(binary), lhs, rhs);
                case MINUS -> new Sub(toSourceRange(binary), lhs, rhs);
                case MUL -> new Mul(toSourceRange(binary), lhs, rhs);
                case DIV -> new DivT(toSourceRange(binary), lhs, rhs);
                case MOD -> new ModT(toSourceRange(binary), lhs, rhs);
                case EQ -> new Eq(toSourceRange(binary), lhs, rhs);
                case NE -> new Neq(toSourceRange(binary), lhs, rhs);
                case LT -> new Lt(toSourceRange(binary), lhs, rhs);
                case LE -> new Le(toSourceRange(binary), lhs, rhs);
                case GT -> new Gt(toSourceRange(binary), lhs, rhs);
                case GE -> new Ge(toSourceRange(binary), lhs, rhs);
                case AND -> new And(toSourceRange(binary), lhs, rhs);
                case OR -> new Or(toSourceRange(binary), lhs, rhs);
                default -> throw new JavaViolationException("Unsupported binary operator: " + binary.getTag());
            };
        }

        private StmtExpr convertUnary(JCTree.JCUnary unary) {
            var inner = convertExpression(unary.arg);
            return switch (unary.getTag()) {
                case NOT -> new Not(toSourceRange(unary), inner);
                case NEG -> new Neg(toSourceRange(unary), inner);
                default -> throw new JavaViolationException("Unsupported unary operator: " + unary.getTag());
            };
        }

        private StmtExpr convertMethodInvocation(JCTree.JCMethodInvocation inv) {
            var jverifyMethod = JVerifyUtils.getJVerifyMethod(inv);
            if (jverifyMethod != null) return convertJVerifyMethod(inv, jverifyMethod);

            // arr.get(idx) in lambdas → arrayIndex
            if (inv.meth instanceof JCTree.JCFieldAccess field &&
                field.name.toString().equals("get") &&
                field.selected.type != null &&
                field.selected.type.getKind() == javax.lang.model.type.TypeKind.ARRAY &&
                inv.args.size() == 1) {
                return new ArrayIndex(toSourceRange(inv), convertExpression(field.selected), convertExpression(inv.args.getFirst()));
            }

            // Static method call - use just the method name
            if (inv.meth instanceof JCTree.JCFieldAccess field) {
                var args = inv.args.stream().map(this::convertExpression).toList();
                return new Call(toSourceRange(inv), new Identifier(toSourceRange(field), field.name.toString()), args);
            }

            var callee = convertExpression(inv.meth);
            var args = inv.args.stream().map(this::convertExpression).toList();
            return new Call(toSourceRange(inv), callee, args);
        }

        private StmtExpr convertJVerifyMethod(JCTree.JCMethodInvocation inv, Symbol.MethodSymbol method) {
            var name = method.getQualifiedName().toString();
            var args = inv.args;

            return switch (name) {
                case "check" -> new Assert(toSourceRange(inv), convertExpression(args.getFirst()));
                case "assume" -> new Assume(toSourceRange(inv), convertExpression(args.getFirst()));
                case "implies" -> new Implies(toSourceRange(inv), convertExpression(args.get(0)), convertExpression(args.get(1)));
                case "forall" -> convertQuantifier(inv, true);
                case "exists" -> convertQuantifier(inv, false);
                case "old" -> new Identifier(toSourceRange(inv), "old$" + getIdentName(args.getFirst()));
                case "result" -> new Identifier(toSourceRange(inv), "result");
                case "sequence" -> staticCall(toSourceRange(inv), "Seq.From", convertExpression(args.getFirst()));
                case "get" -> staticCall(toSourceRange(inv), "Seq.Get", convertExpression(getReceiver(inv)), convertExpression(args.getFirst()));
                case "drop" -> staticCall(toSourceRange(inv), "Seq.Drop", convertExpression(getReceiver(inv)), convertExpression(args.getFirst()));
                case "take" -> staticCall(toSourceRange(inv), "Seq.Take", convertExpression(getReceiver(inv)), convertExpression(args.getFirst()));
                case "contains" -> staticCall(toSourceRange(inv), "Seq.Contains", convertExpression(getReceiver(inv)), convertExpression(args.getFirst()));
                case "size" -> staticCall(toSourceRange(inv), "Seq.Size", convertExpression(getReceiver(inv)));
                default -> throw new JavaViolationException("Unsupported JVerify method: " + name);
            };
        }

        private StmtExpr staticCall(SourceRange range, String name, StmtExpr... args) {
            return new Call(range, new Identifier(range, name), List.of(args));
        }

        private String getIdentName(JCTree.JCExpression expr) {
            if (expr instanceof JCTree.JCIdent ident) return ident.name.toString();
            throw new JavaViolationException("Expected identifier in old()");
        }

        private StmtExpr convertQuantifier(JCTree.JCMethodInvocation inv, boolean isForall) {
            if (!(inv.args.getFirst() instanceof JCTree.JCLambda lambda))
                throw new JavaViolationException("Quantifier requires lambda argument");

            var body = convertExpression((JCTree.JCExpression) lambda.body);
            for (int i = lambda.params.size() - 1; i >= 0; i--) {
                var p = lambda.params.get(i);
                var ty = translateType(p.vartype);
                body = isForall ? new ForallExpr(toSourceRange(p), p.name.toString(), ty, body)
                               : new ExistsExpr(toSourceRange(p), p.name.toString(), ty, body);
            }
            return body;
        }

        private JCTree.JCExpression getReceiver(JCTree.JCMethodInvocation inv) {
            if (inv.meth instanceof JCTree.JCFieldAccess field) return field.selected;
            throw new JavaViolationException("Expected method call with receiver");
        }
    }
}
