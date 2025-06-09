package com.aws.jverify.verifier;

import com.aws.jverify.JVerify;
import com.aws.jverify.generated.*;
import com.sun.source.tree.Tree;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.TypeTag;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.tree.TreeMaker;
import org.checkerframework.checker.nullness.qual.Nullable;

import javax.lang.model.type.ArrayType;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ExpressionCompiler {
    JavaToDafnyCompiler compiler;

    public ExpressionCompiler(JavaToDafnyCompiler compiler) {
        this.compiler = compiler;
    }

    public Expression toExpr(JCTree tree) {
        if (tree instanceof JCTree.JCExpression expression) {
            return toExpr(expression);
        }
        compiler.reportError(tree, "notSupported", tree.getClass().getSimpleName() + " as an expression");
        return JavaToDafnyCompiler.getHole(compiler.toOrigin(tree));
    }
    
    @Nullable
    BinaryExprOpcode toDafny(Symbol.OperatorSymbol operator) {
        return switch (operator.name.toString()) {
            case "-" -> BinaryExprOpcode.Sub;
            case "+" -> BinaryExprOpcode.Add;
            case "*" -> BinaryExprOpcode.Mul;
            case "/" -> BinaryExprOpcode.Div;
            case "==" -> BinaryExprOpcode.Eq;
            case "!=" -> BinaryExprOpcode.Neq;
            case "<" -> BinaryExprOpcode.Lt;
            case "<=" -> BinaryExprOpcode.Le;
            case ">" -> BinaryExprOpcode.Gt;
            case ">=" -> BinaryExprOpcode.Ge;
            case "||" -> BinaryExprOpcode.Or;
            case "&&" -> BinaryExprOpcode.And;
            case "%" -> BinaryExprOpcode.Mod;
            case "&" -> BinaryExprOpcode.BitwiseAnd;
            case "|" -> BinaryExprOpcode.BitwiseOr;
            case "^" -> BinaryExprOpcode.BitwiseXor;
            default -> null;
        };
    }

    private Expression translateSwitchExpression(JCTree.JCSwitchExpression switchExpr) {
        var origin = compiler.toOrigin(switchExpr);
        var patternBodies = new Patterns(compiler).translateSwitchLabels(switchExpr);
        if (patternBodies == null) {
            return JavaToDafnyCompiler.getHole(origin);
        }

        var translatedCases = patternBodies.stream().map(patternBody -> {
            var caseOrigin = compiler.toOrigin(patternBody.cas());
            var body = patternBody.body();
            final Expression translatedBody;

            // A switch rule introduces either an expression, a block, or a throw statement.
            if (body instanceof JCTree.JCExpression) {
                translatedBody = toExpr(body);
            } else {
                var bodyKind = body instanceof JCTree.JCBlock ? "block" : "throw statement";
                compiler.reportError(body, "notSupported", "switch rule %s".formatted(bodyKind));
                translatedBody = JavaToDafnyCompiler.getHole(caseOrigin);
            }
            return new NestedMatchCaseExpr(caseOrigin, patternBody.pattern(), translatedBody, null);
        }).toList();

        var source = toExpr(switchExpr.getExpression());
        return new NestedMatchExpr(origin, source, translatedCases, true, null);
    }



    public Expression toExpr(JCTree.JCExpression expr) {
        return toExpr(expr, null);
    }

    public Expression toExpr(JCTree.JCExpression expr, IOrigin originOverride) {
        var origin = Objects.requireNonNullElseGet(originOverride, () -> compiler.toOrigin(expr));
        switch (expr) {
            case JCTree.JCConditional conditional -> {
                var condition = toExpr(conditional.getCondition());
                var thenBranch = toExpr(conditional.getTrueExpression());
                var elseBranch = toExpr(conditional.getFalseExpression());
                return new ITEExpr(origin, false, condition, thenBranch, elseBranch);
            }
            case JCTree.JCSwitchExpression switchExpr -> {
                return translateSwitchExpression(switchExpr);
            }
            case JCTree.JCUnary unary -> {
                var innerExpr = toExpr(unary.getExpression());
                switch (unary.getTag()) {
                    case JCTree.Tag.POSTINC, POSTDEC, JCTree.Tag.PREINC, JCTree.Tag.PREDEC -> {
                        compiler.reportError(expr, "mutatingExpression", unary.getOperator().name.toString());
                        return compiler.getHole(origin);
                    }
                    case JCTree.Tag.NOT -> {
                        return new UnaryOpExpr(origin, innerExpr, UnaryOpExprOpcode.Not);
                    }
                    case JCTree.Tag.NEG -> {
                        return new NegationExpression(origin, innerExpr);
                    }
                    case JCTree.Tag.POS -> {
                        return innerExpr;
                    }
                    default -> {
                        compiler.reportError(unary, "notSupported", "operator " + unary.getOperator());
                        return compiler.getHole(origin);
                    }
                }
            }
            case JCTree.JCBinary binary -> {
                var left = toExpr(binary.getLeftOperand());
                var right = toExpr(binary.getRightOperand());
                Symbol.OperatorSymbol operator = binary.getOperator();
                return translateBinary(binary, binary.type, binary.getLeftOperand().type, operator, left, right);
            }
            case JCTree.JCIdent identifier -> {
                var identName = compiler.nameMangler.mangleSymbolName(identifier.sym);
                if (identName.contentEquals("this")) {
                    return new ThisExpr(origin);
                }
                return new NameSegment(origin, identName, null);
            }
            case JCTree.JCLiteral literal -> {
                if (literal.typetag == TypeTag.BOOLEAN) {
                    return new LiteralExpr(compiler.toOrigin(literal), literal.getValue());
                }
                if (literal.typetag == TypeTag.CHAR) {
                    var intValue = Integer.valueOf((char) literal.getValue());
                    return new LiteralExpr(compiler.toOrigin(literal), intValue);
                }
                if (expr.getKind().equals(Tree.Kind.STRING_LITERAL)) {
                    return translateStringLiteral(compiler.toOrigin(literal), literal);
                }
                return new LiteralExpr(origin, literal.getValue());
            }
            case JCTree.JCMethodInvocation invocation -> {
                var jverifyMethodExpr = jverifyLibMethodToExpr(invocation);
                if (jverifyMethodExpr != null) {
                    return jverifyMethodExpr;
                }

                var methodSymbol = TreeInfo.symbol(invocation.getMethodSelect());
                var argBindings = invocation.getArguments().stream().map(a -> new ActualBinding(null, toExpr(a), false)).toList();

                if (methodSymbol.owner instanceof Symbol.ClassSymbol ownerClass
                        && ownerClass.fullname.contentEquals(String.class.getName())) {
                    final Expression receiver;
                    if (invocation.getMethodSelect() instanceof JCTree.JCFieldAccess fieldAccess) {
                        receiver = toExpr(fieldAccess.selected);
                    } else {
                        compiler.reportError(invocation, "notSupported", "method reference call");
                        receiver = compiler.getHole(origin);
                    }

                    return switch (methodSymbol.name.toString()) {
                        case "charAt" -> new SeqSelectExpr(origin, true, receiver,
                                argBindings.getFirst().getActual(), null, null);
                        case "equals" -> new BinaryExpr(origin, BinaryExprOpcode.Eq, receiver,
                                argBindings.getFirst().getActual());
                        case "isEmpty" -> new BinaryExpr(origin, BinaryExprOpcode.Eq, receiver,
                                new SeqDisplayExpr(origin, List.of()));
                        case "length" -> new UnaryOpExpr(
                                origin, receiver, UnaryOpExprOpcode.Cardinality);
                        case "substring" -> {
                            var endIndex = argBindings.size() == 2
                                    ? argBindings.get(1).getActual()
                                    : new UnaryOpExpr(origin, receiver, UnaryOpExprOpcode.Cardinality);
                            yield new SeqSelectExpr(origin, false, receiver,
                                    argBindings.getFirst().getActual(), endIndex, null);
                        }
                        default -> {
                            compiler.reportError(invocation, "notSupported", "String method " + methodSymbol);
                            yield compiler.getHole(origin);
                        }
                    };
                }

                var target = toExpr(invocation.getMethodSelect());
                return new ApplySuffix(origin, target, null,
                        new ActualBindings(argBindings), null);
            }
            case JCTree.JCFieldAccess fieldAccess -> {
                if (fieldAccess.sym instanceof Symbol.ClassSymbol classSymbol) {
                    return new NameSegment(origin, compiler.nameMangler.mangleSymbolName(classSymbol), List.of());
                }
                var selectedExpr = toExpr(fieldAccess.selected);
                // TODO does this work if the selected expression isn't trivially of array type?
                if (fieldAccess.selected.type instanceof ArrayType && fieldAccess.name.contentEquals("length")) {
                    return new ExprDotName(origin, selectedExpr, compiler.getName(fieldAccess, "Length"), null);
                }

                var fieldName = compiler.nameMangler.mangleSymbolName(fieldAccess.sym);
                if (compiler.isEnum(fieldAccess.selected)) {
                    return new ApplySuffix(origin, new NameSegment(origin, fieldName, null),
                            null, new ActualBindings(List.of()), null);
                } else {
                    return new ExprDotName(origin, toExpr(fieldAccess.selected), compiler.getName(fieldAccess, fieldName), null);
                }
            }
            case JCTree.JCArrayAccess arrayAccess -> {
                var arrayExpr = toExpr(arrayAccess.getExpression());
                var indexExpr = toExpr(arrayAccess.getIndex());
                return new SeqSelectExpr(origin, true, arrayExpr, indexExpr, null, null);
            }
            case JCTree.JCParens parens -> {
                return toExpr(parens.getExpression());
            }
            case JCTree.JCAssignOp assignOp -> {
                compiler.reportError(expr, "mutatingExpression", assignOp.getOperator().name.toString() + "=");
                return compiler.getHole(origin);
            }
            case JCTree.JCInstanceOf instanceOf -> {
                var expression = toExpr(instanceOf.getExpression());
                var jcType = compiler.translateType(instanceOf.getType());
                return new TypeTestExpr(origin, expression, jcType);
            }
            case JCTree.JCTypeCast cast -> {
                var castExpr = toExpr(cast.getExpression());
                var type = compiler.translateType(cast.getType());
                return new ConversionExpr(origin, castExpr, type, "");
            }
            case JCTree.JCLambda lambda -> {
                var types = Types.instance(compiler.context);
                var methodSymbol = (Symbol.MethodSymbol) types.findDescriptorSymbol(lambda.target.tsym);
                var maker = TreeMaker.instance(compiler.context);
                var methodDecl = compiler.translateMethodOrLambda(lambda, maker.Modifiers(0), methodSymbol, lambda.getBody(), List.of());

                var datatypeName = "Lambda" + compiler.lambdaDatatypeDecls.size();
                var datatypeNameNode = new Name(origin, datatypeName);
                var datatypeCtor = new DatatypeCtor(origin, datatypeNameNode, null, false, List.of());
                var trait = compiler.translateType(lambda.target, origin);
                var datatypeDecl = new IndDatatypeDecl(origin, datatypeNameNode, null, List.of(), List.of(methodDecl),
                        List.of(trait), List.of(datatypeCtor), false);
                compiler.lambdaDatatypeDecls.add(datatypeDecl);

                // TODO: Using a DatatypeValue directly ends up crashing when printing temp.dfy,
                // because the printer tries to read DatatypeValue.Arguments before it's filled in by resolution.
//                return new DatatypeValue(origin, datatypeName, datatypeName, new ActualBindings(List.of()));
                return new ExprDotName(origin, new NameSegment(origin, datatypeName, null), datatypeNameNode, null);
            }
            case JCTree.JCTypeApply typeApply -> {
                var type = this.toExpr(typeApply.getType());
                if (type instanceof NameSegment nameSegment) {
                    List<Type> arguments;
                    if (typeApply.getTypeArguments().isEmpty()) {
                        // Occurs when the type arguments were inferred
                        arguments = typeApply.type.getTypeArguments().stream().map(t -> compiler.translateType(t, false, origin)).toList();
                    } else {
                        arguments = typeApply.getTypeArguments().stream().map(compiler::translateType).toList();
                    }
                    return new NameSegment(origin, nameSegment.getName(), arguments);
                }
                throw new RuntimeException("All Dafny type references are NameSegments, since we do not use Dafny modules");
            }
            case null, default -> {
            }
        }
        compiler.reportError(expr, "notSupported", expr.getClass().getSimpleName());
        return compiler.getHole(origin);
    }

    public Expression translateBinary(JCTree node,
                                      com.sun.tools.javac.code.Type resultType,
                                      com.sun.tools.javac.code.Type leftType,
                                      Symbol.OperatorSymbol operator, Expression left, Expression right) {
        var origin = compiler.toOrigin(node);
        if (leftType.getTag() == TypeTag.FLOAT || leftType.getTag() == TypeTag.DOUBLE) {
            compiler.reportError(node, "notSupported", "operator " + operator);
        }
        var isBitwise = switch (operator.name.toString()) {
            case "&", "|", "^", "<<", ">>", ">>>" -> true;
            default -> false;
        };

        if (isBitwise) {
            compiler.reportError(node, "notSupported", "operator " + operator);
            return compiler.getHole(origin);
        }
        BinaryExprOpcode dafnyOperator = toDafny(operator);
        if (dafnyOperator == null) {
            compiler.reportError(node, "notSupported", "operator " + operator);
            return compiler.getHole(origin);
        }
        return new BinaryExpr(origin, dafnyOperator, left, right);
    }

    /**
     * Translates the specified library method invocation to a Dafny expression,
     * or returns {@code null} if the invocation is not a JVerify library method.
     *
     * <p>Note: header methodContracts like {@link JVerify#precondition(boolean)}
     * and {@link JVerify#postcondition(boolean)}
     * must be translated by {@link MethodCompiler#translateStatement(JCTree.JCStatement)},
     * not here.
     */
    private @Nullable Expression jverifyLibMethodToExpr(JCTree.JCMethodInvocation invocation) {
        var jverifyMethod = JavaToDafnyCompiler.getJVerifyMethod(invocation);
        if (jverifyMethod == null) {
            return null;
        }

        var origin = compiler.toOrigin(invocation);
        var receiver = invocation.getMethodSelect() instanceof JCTree.JCFieldAccess fieldAccess
                ? fieldAccess.selected
                : null;
        var methodName = jverifyMethod.getQualifiedName().toString();
        var args = invocation.getArguments();
        switch (methodName) {
            case "forall", "exists" -> {
                if (args.size() != 1) {
                    throw new JavaViolationException("A %s call must have exactly one argument".formatted(methodName));
                }
                if (!(args.getFirst() instanceof JCTree.JCLambda lambda)) {
                    compiler.reportError(args.getFirst(), "argumentMustBeLambda", methodName);
                    return null;
                }
                var boundVars = lambda.params.stream().map(param -> {
                    var paramOrigin = compiler.toOrigin(lambda);
                    var paramName = new Name(paramOrigin, param.getName().toString());
                    var paramType = compiler.translateType(param.getType().type, false, paramOrigin);
                    return new BoundVar(paramOrigin, paramName, paramType, false);
                }).toList();
                var body = toExpr(lambda.getBody());
                if (body == null) {
                    return null;
                }
                if ("forall".equals(methodName)) {
                    return new ForallExpr(origin, boundVars, null, body, null);
                } else {
                    return new ExistsExpr(origin, boundVars, null, body, null);
                }
            }
            case "sequence" -> {
                // array conversion to sequence by appending "[..]", optionally with lo/hi
                var array = args.get(0);
                var fromIndex = args.length() > 1 ? args.get(1) : null;
                var toIndex = args.length() > 2 ? args.get(2) : null;
                return toSubsequence(origin, array, fromIndex, toIndex);
            }
            case "drop" -> {
                return toSubsequence(origin, receiver, args.getFirst(), null);
            }
            case "take" -> {
                return toSubsequence(origin, receiver, null, args.getFirst());
            }
            case "subsequence" -> {
                return toSubsequence(origin, receiver, args.get(0), args.get(1));
            }
            case "contains" -> {
                var element = toExpr(args.getFirst());
                var seq = toExpr(receiver);
                return new BinaryExpr(compiler.toOrigin(invocation), BinaryExprOpcode.In, element, seq);
            }
            case "old" -> {
                var element = toExpr(args.getFirst());
                return new OldExpr(compiler.toOrigin(invocation), element, null);
            }
            case "fresh" -> {
                var element = toExpr(args.getFirst());
                return new FreshExpr(compiler.toOrigin(invocation), element, null);
            }
        }

        compiler.reportError(invocation.getMethodSelect(), "notSupported", "library method %s".formatted(jverifyMethod));
        return null;
    }

    private SeqSelectExpr toSubsequence(IOrigin origin, JCTree.JCExpression seqOrArray, JCTree.@Nullable JCExpression lo, JCTree.@Nullable JCExpression hi) {
        var seqOrArrayExpr = toExpr(seqOrArray);
        var loExpr = lo == null ? null : toExpr(lo);
        var hiExpr = hi == null ? null : toExpr(hi);
        return new SeqSelectExpr(origin, false, seqOrArrayExpr, loExpr, hiExpr, null);
    }


    /**
     * Translates the given string literal to a Dafny expression (of type {@code jstring}).
     * For ease of debugging, the translation is the equivalent Dafny string literal
     * if all characters in the string are printable ASCII or have (non-Unicode) escape sequences.
     * Otherwise, the translation is a sequence display of the characters' numeric values.
     */
    private static Expression translateStringLiteral(IOrigin origin, JCTree.JCLiteral literal) {
        assert literal.getKind().equals(Tree.Kind.STRING_LITERAL);
        var stringValue = (String) literal.getValue();

        var translatedChars = new StringBuilder();
        for (var charValue : stringValue.toCharArray()) {
            if (ESCAPED_CHARS.containsKey(charValue)) {
                translatedChars.append(ESCAPED_CHARS.get(charValue));
            } else if (charValue >= 0x20 && charValue <= 0x7e) {
                translatedChars.append(charValue);
            } else {
                // Fallback to sequence of numeric values
                var charExprs = stringValue.chars().boxed()
                        .map(c -> (Expression) new LiteralExpr(origin, c)).toList();
                return new SeqDisplayExpr(origin, charExprs);
            }
        }

        var stringExpr = new StringLiteralExpr(origin, translatedChars.toString(), false);
        return new ApplySuffix(origin, new NameSegment(origin, "JString", null), null,
                new ActualBindings(List.of(new ActualBinding(null, stringExpr, false))), null);
    }

    private static final Map<Character, String> ESCAPED_CHARS = Map.of(
            '\'', "\\'",
            '\"', "\\\"",
            '\\', "\\\\",
            '\0', "\\0",
            '\n', "\\n",
            '\r', "\\r",
            '\t', "\\t"
    );
}
