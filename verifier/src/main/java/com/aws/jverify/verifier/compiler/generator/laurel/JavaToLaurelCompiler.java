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
            var sr = toSourceRange(compilationUnit);
            topLevels.addAll(visitor.procedures.stream()
                    .map(p -> (TopLevel) Laurel.topLevelProcedure(sr, p))
                    .toList());
            result.add(new LaurelFile(compilationUnit.sourcefile.toUri(),
                    Laurel.program(sr, topLevels)));
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
        var sr = toSourceRange(currentCompilationUnit);
        var x = Laurel.identifier(sr, "x");
        StmtExpr constraint;
        if (max == null) {
            constraint = Laurel.ge(sr, x, Laurel.int_(sr, min));
        } else {
            constraint = Laurel.and(sr,
                Laurel.ge(sr, x, min >= 0 ? Laurel.int_(sr, min) : Laurel.neg(sr, Laurel.int_(sr, -min))),
                Laurel.le(sr, x, Laurel.int_(sr, max)));
        }
        return Laurel.topLevelConstrainedType(sr, Laurel.constrainedType(sr, name, "x", Laurel.intType(sr), constraint, Laurel.int_(sr, 0)));
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
                        params.add(Laurel.parameter(toSourceRange(param), param.name.toString(), translateType(param.vartype)));
                    }

                    Optional<OptionalReturnType> returnType = Optional.empty();
                    if (method.restype != null && !isVoid(method.restype)) {
                        returnType = Optional.of(Laurel.optionalReturnType(toSourceRange(method.restype), translateType(method.restype)));
                    }

                    var contracts = extractContracts(method.body);
                    StmtExpr body = convertMethodBody(contracts.bodyStatements);

                    procedures.add(Laurel.procedure(toSourceRange(method), methodName, params, returnType,
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
                var sr = toSourceRange(prim);
                return switch (prim.typetag) {
                    case INT -> Laurel.compositeType(sr, "int32");
                    case LONG -> Laurel.intType(sr);
                    case BOOLEAN -> Laurel.boolType(sr);
                    default -> throw new JavaViolationException("Unsupported primitive type: " + prim.typetag);
                };
            } else if (tree instanceof JCTree.JCArrayTypeTree arr) {
                return Laurel.arrayType(toSourceRange(arr), translateType(arr.elemtype));
            } else if (tree instanceof JCTree.JCIdent ident) {
                return Laurel.compositeType(toSourceRange(ident), ident.name.toString());
            } else if (tree instanceof JCTree.JCTypeApply typeApply) {
                // Generic type like JArray<T> - just use the base type name for now
                if (typeApply.clazz instanceof JCTree.JCIdent ident) {
                    return Laurel.compositeType(toSourceRange(typeApply), ident.name.toString());
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
                requires.add(Laurel.requiresClause(toSourceRange(pre.get()), convertExpression(pre.get())));
            }
            for (var post : contract.postconditions()) {
                ensures.add(Laurel.ensuresClause(toSourceRange(post.get()), convertPostcondition(post.get())));
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
                case Identifier id -> id.name().equals(from) ? Laurel.identifier(id.sourceRange(), to) : id;
                case Add op -> Laurel.add(op.sourceRange(), sub(op.lhs(), from, to), sub(op.rhs(), from, to));
                case Sub op -> Laurel.sub(op.sourceRange(), sub(op.lhs(), from, to), sub(op.rhs(), from, to));
                case Mul op -> Laurel.mul(op.sourceRange(), sub(op.lhs(), from, to), sub(op.rhs(), from, to));
                case Div op -> Laurel.div(op.sourceRange(), sub(op.lhs(), from, to), sub(op.rhs(), from, to));
                case Mod op -> Laurel.mod(op.sourceRange(), sub(op.lhs(), from, to), sub(op.rhs(), from, to));
                case Eq op -> Laurel.eq(op.sourceRange(), sub(op.lhs(), from, to), sub(op.rhs(), from, to));
                case Neq op -> Laurel.neq(op.sourceRange(), sub(op.lhs(), from, to), sub(op.rhs(), from, to));
                case Lt op -> Laurel.lt(op.sourceRange(), sub(op.lhs(), from, to), sub(op.rhs(), from, to));
                case Le op -> Laurel.le(op.sourceRange(), sub(op.lhs(), from, to), sub(op.rhs(), from, to));
                case Gt op -> Laurel.gt(op.sourceRange(), sub(op.lhs(), from, to), sub(op.rhs(), from, to));
                case Ge op -> Laurel.ge(op.sourceRange(), sub(op.lhs(), from, to), sub(op.rhs(), from, to));
                case And op -> Laurel.and(op.sourceRange(), sub(op.lhs(), from, to), sub(op.rhs(), from, to));
                case Or op -> Laurel.or(op.sourceRange(), sub(op.lhs(), from, to), sub(op.rhs(), from, to));
                case Implies op -> Laurel.implies(op.sourceRange(), sub(op.lhs(), from, to), sub(op.rhs(), from, to));
                case Not op -> Laurel.not(op.sourceRange(), sub(op.inner(), from, to));
                case Neg op -> Laurel.neg(op.sourceRange(), sub(op.inner(), from, to));
                case ArrayIndex ai -> Laurel.arrayIndex(ai.sourceRange(), sub(ai.arr(), from, to), sub(ai.idx(), from, to));
                case FieldAccess fa -> Laurel.fieldAccess(fa.sourceRange(), sub(fa.obj(), from, to), fa.field());
                case Call c -> Laurel.call(c.sourceRange(), sub(c.callee(), from, to), c.args().stream().map(a -> sub(a, from, to)).toList());
                default -> expr;
            };
        }

        private StmtExpr sub(StmtExpr e, String from, String to) { return substituteIdent(e, from, to); }

        private StmtExpr convertMethodBody(List<JCTree.JCStatement> statements) {
            if (statements.isEmpty()) return Laurel.block(toSourceRange(currentCompilationUnit), List.of());
            List<StmtExpr> stmts = new ArrayList<>();
            for (var statement : statements) {
                StmtExpr converted = convertStatement(statement);
                if (converted != null) stmts.add(converted);
            }
            return Laurel.block(toSourceRange(statements.getFirst()), stmts);
        }

        private StmtExpr convertStatement(JCTree.JCStatement statement) {
            if (statement instanceof JCTree.JCAssert assertStmt) {
                return Laurel.assert_(toSourceRange(assertStmt), convertExpression(assertStmt.cond));
            } else if (statement instanceof JCTree.JCExpressionStatement exprStmt) {
                return convertExpressionStatement(exprStmt);
            } else if (statement instanceof JCTree.JCBlock block) {
                List<StmtExpr> stmts = new ArrayList<>();
                for (var s : block.stats) {
                    var converted = convertStatement(s);
                    if (converted != null) stmts.add(converted);
                }
                return Laurel.block(toSourceRange(block), stmts);
            } else if (statement instanceof JCTree.JCReturn ret) {
                return Laurel.return_(toSourceRange(ret), ret.expr != null ? convertExpression(ret.expr) : Laurel.literalBool(toSourceRange(ret), true));
            } else if (statement instanceof JCTree.JCVariableDecl varDecl) {
                Optional<OptionalAssignment> init = varDecl.init != null ? Optional.of(Laurel.optionalAssignment(toSourceRange(varDecl.init), convertExpression(varDecl.init)))
                                                : Optional.empty();
                return Laurel.varDecl(toSourceRange(varDecl), varDecl.name.toString(),
                    Optional.of(Laurel.optionalType(toSourceRange(varDecl.vartype), translateType(varDecl.vartype))), init);
            } else if (statement instanceof JCTree.JCIf ifStmt) {
                Optional<OptionalElse> elseBranch = ifStmt.elsepart != null
                    ? Optional.of(Laurel.optionalElse(toSourceRange(ifStmt.elsepart), convertStatement(ifStmt.elsepart)))
                    : Optional.empty();
                return Laurel.ifThenElse(toSourceRange(ifStmt), convertExpression(ifStmt.cond), convertStatement(ifStmt.thenpart), elseBranch);
            } else if (statement instanceof JCTree.JCWhileLoop whileLoop) {
                var contract = contractCompiler.getContract(whileLoop.body);
                List<InvariantClause> invariants = new ArrayList<>();
                for (var inv : contract.loopInvariants()) {
                    invariants.add(Laurel.invariantClause(toSourceRange(inv.get()), convertExpression(inv.get())));
                }
                var bodyStmts = MethodOrLoopContractCompiler.getImplementationStatements(whileLoop.body);
                return Laurel.while_(toSourceRange(whileLoop), convertExpression(whileLoop.cond), invariants, convertMethodBody(new ArrayList<>(bodyStmts)));
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
                return Laurel.identifier(toSourceRange(ident), ident.name.toString());
            } else if (expr instanceof JCTree.JCBinary binary) {
                return convertBinary(binary);
            } else if (expr instanceof JCTree.JCUnary unary) {
                return convertUnary(unary);
            } else if (expr instanceof JCTree.JCParens parens) {
                // Skip explicit parentheses - formatter will add them based on precedence
                return convertExpression(parens.expr);
            } else if (expr instanceof JCTree.JCAssign assign) {
                return Laurel.assign(toSourceRange(assign), convertExpression(assign.lhs), convertExpression(assign.rhs));
            } else if (expr instanceof JCTree.JCArrayAccess arr) {
                return Laurel.arrayIndex(toSourceRange(arr), convertExpression(arr.indexed), convertExpression(arr.index));
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
                        var sr = toSourceRange(field);
                        return v >= 0 ? Laurel.int_(sr, v) : Laurel.neg(sr, Laurel.int_(sr, -v));
                    }
                }
                return Laurel.fieldAccess(toSourceRange(field), convertExpression(field.selected), field.name.toString());
            } else if (expr instanceof JCTree.JCMethodInvocation inv) {
                return convertMethodInvocation(inv);
            } else if (expr instanceof JCTree.JCConditional cond) {
                return Laurel.ifThenElse(toSourceRange(cond), convertExpression(cond.cond), convertExpression(cond.truepart),
                    Optional.of(Laurel.optionalElse(toSourceRange(cond.falsepart), convertExpression(cond.falsepart))));
            }
            throw new JavaViolationException("Unsupported expression: " + expr.getClass().getName());
        }

        private StmtExpr convertLiteral(JCTree.JCLiteral literal) {
            var sr = toSourceRange(literal);
            return switch (literal.typetag) {
                case BOOLEAN -> Laurel.literalBool(sr, (int)literal.value != 0);
                case INT, LONG -> {
                    long val = ((Number)literal.value).longValue();
                    yield val >= 0 ? Laurel.int_(sr, val) : Laurel.neg(sr, Laurel.int_(sr, -val));
                }
                default -> throw new JavaViolationException("Unsupported literal type: " + literal.typetag);
            };
        }

        private StmtExpr convertBinary(JCTree.JCBinary binary) {
            var sr = toSourceRange(binary);
            var lhs = convertExpression(binary.lhs);
            var rhs = convertExpression(binary.rhs);
            return switch (binary.getTag()) {
                case PLUS -> Laurel.add(sr, lhs, rhs);
                case MINUS -> Laurel.sub(sr, lhs, rhs);
                case MUL -> Laurel.mul(sr, lhs, rhs);
                case DIV -> Laurel.divT(sr, lhs, rhs);
                case MOD -> Laurel.modT(sr, lhs, rhs);
                case EQ -> Laurel.eq(sr, lhs, rhs);
                case NE -> Laurel.neq(sr, lhs, rhs);
                case LT -> Laurel.lt(sr, lhs, rhs);
                case LE -> Laurel.le(sr, lhs, rhs);
                case GT -> Laurel.gt(sr, lhs, rhs);
                case GE -> Laurel.ge(sr, lhs, rhs);
                case AND -> Laurel.and(sr, lhs, rhs);
                case OR -> Laurel.or(sr, lhs, rhs);
                default -> throw new JavaViolationException("Unsupported binary operator: " + binary.getTag());
            };
        }

        private StmtExpr convertUnary(JCTree.JCUnary unary) {
            var sr = toSourceRange(unary);
            var inner = convertExpression(unary.arg);
            return switch (unary.getTag()) {
                case NOT -> Laurel.not(sr, inner);
                case NEG -> Laurel.neg(sr, inner);
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
                return Laurel.arrayIndex(toSourceRange(inv), convertExpression(field.selected), convertExpression(inv.args.getFirst()));
            }

            // Static method call - use just the method name
            if (inv.meth instanceof JCTree.JCFieldAccess field) {
                var args = inv.args.stream().map(this::convertExpression).toList();
                return Laurel.call(toSourceRange(inv), Laurel.identifier(toSourceRange(field), field.name.toString()), args);
            }

            var callee = convertExpression(inv.meth);
            var args = inv.args.stream().map(this::convertExpression).toList();
            return Laurel.call(toSourceRange(inv), callee, args);
        }

        private StmtExpr convertJVerifyMethod(JCTree.JCMethodInvocation inv, Symbol.MethodSymbol method) {
            var name = method.getQualifiedName().toString();
            var args = inv.args;
            var sr = toSourceRange(inv);

            return switch (name) {
                case "check" -> Laurel.assert_(sr, convertExpression(args.getFirst()));
                case "assume" -> Laurel.assume(sr, convertExpression(args.getFirst()));
                case "implies" -> Laurel.implies(sr, convertExpression(args.get(0)), convertExpression(args.get(1)));
                case "forall" -> convertQuantifier(inv, true);
                case "exists" -> convertQuantifier(inv, false);
                case "old" -> Laurel.identifier(sr, "old$" + getIdentName(args.getFirst()));
                case "result" -> Laurel.identifier(sr, "result");
                case "sequence" -> staticCall(sr, "Seq.From", convertExpression(args.getFirst()));
                case "get" -> staticCall(sr, "Seq.Get", convertExpression(getReceiver(inv)), convertExpression(args.getFirst()));
                case "drop" -> staticCall(sr, "Seq.Drop", convertExpression(getReceiver(inv)), convertExpression(args.getFirst()));
                case "take" -> staticCall(sr, "Seq.Take", convertExpression(getReceiver(inv)), convertExpression(args.getFirst()));
                case "contains" -> staticCall(sr, "Seq.Contains", convertExpression(getReceiver(inv)), convertExpression(args.getFirst()));
                case "size" -> staticCall(sr, "Seq.Size", convertExpression(getReceiver(inv)));
                default -> throw new JavaViolationException("Unsupported JVerify method: " + name);
            };
        }

        private StmtExpr staticCall(SourceRange range, String name, StmtExpr... args) {
            return Laurel.call(range, Laurel.identifier(range, name), List.of(args));
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
                body = isForall ? Laurel.forallExpr(toSourceRange(p), p.name.toString(), ty, body)
                               : Laurel.existsExpr(toSourceRange(p), p.name.toString(), ty, body);
            }
            return body;
        }

        private JCTree.JCExpression getReceiver(JCTree.JCMethodInvocation inv) {
            if (inv.meth instanceof JCTree.JCFieldAccess field) return field.selected;
            throw new JavaViolationException("Expected method call with receiver");
        }
    }
}
