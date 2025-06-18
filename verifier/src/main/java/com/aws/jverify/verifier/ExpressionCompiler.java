package com.aws.jverify.verifier;

import com.aws.jverify.JVerify;
import com.aws.jverify.generated.*;
import com.sun.source.tree.Tree;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.TypeTag;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.Names;
import org.checkerframework.checker.nullness.qual.Nullable;

import javax.lang.model.element.Modifier;
import javax.lang.model.type.ArrayType;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.sun.tools.javac.code.Flags.SYNTHETIC;

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
                        return JavaToDafnyCompiler.getHole(origin);
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
                        return JavaToDafnyCompiler.getHole(origin);
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
                var identName = compiler.nameCompiler.getCompiledName(identifier.sym);
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

                final Expression receiver;
                if (invocation.getMethodSelect() instanceof JCTree.JCFieldAccess fieldAccess) {
                    receiver = toExpr(fieldAccess.selected);
                } else if (invocation.getMethodSelect() instanceof JCTree.JCIdent) {
                    receiver = null;
                } else {
                    compiler.reportError(invocation, "notSupported", "call via method reference");
                    receiver = JavaToDafnyCompiler.getHole(origin);
                }

                if (methodSymbol.owner instanceof Symbol.ClassSymbol ownerClass
                        && ownerClass.isRecord()
                        && invocation.getArguments().isEmpty()
                ) {
                    var component = ownerClass.getRecordComponents().stream()
                            .filter(comp -> comp.name.equals(methodSymbol.name))
                            .findAny();
                    if (component.isPresent()) {
                        var fieldNameStr = compiler.nameCompiler.getCompiledName(component.get());
                        var fieldName = compiler.getName(invocation.getMethodSelect(), fieldNameStr);
                        return new ExprDotName(origin, receiver, fieldName, null);
                    }
                }

                if (methodSymbol.owner instanceof Symbol.ClassSymbol ownerClass
                        && ownerClass.fullname.contentEquals(String.class.getName())) {
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
                    return new NameSegment(origin, compiler.nameCompiler.getCompiledName(classSymbol), List.of());
                }
                if (fieldAccess.sym instanceof Symbol.DynamicMethodSymbol dynamicMethodSymbol) {
                    return translateDynamicMethod(origin, fieldAccess, dynamicMethodSymbol);
                }
                var selectedExpr = toExpr(fieldAccess.selected);
                // TODO does this work if the selected expression isn't trivially of array type?
                if (fieldAccess.selected.type instanceof ArrayType && fieldAccess.name.contentEquals("length")) {
                    return new ExprDotName(origin, selectedExpr, compiler.getName(fieldAccess, "Length"), null);
                }

                var fieldName = compiler.nameCompiler.getCompiledName(fieldAccess.sym);
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
                return JavaToDafnyCompiler.getHole(origin);
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
            case JCTree.JCLambda _ ->
                throw new RuntimeException("Lambdas should have been rewritten, but found one at " + origin);
            case JCTree.JCMemberReference _ ->
                throw new RuntimeException("Member references should have been rewritten, but found one at " + origin);
            case JCTree.JCTypeApply typeApply -> {
                var type = toExpr(typeApply.getType());
                if (type instanceof NameSegment nameSegment) {
                    List<Type> arguments;
                    if (typeApply.getTypeArguments().isEmpty()) {
                        // Occurs when the type arguments were inferred
                        arguments = typeApply.type.getTypeArguments().stream().map(t -> compiler.translateType(null, t, origin)).toList();
                    } else {
                        arguments = typeApply.getTypeArguments().stream().map(compiler::translateType).toList();
                    }
                    return new NameSegment(origin, nameSegment.getName(), arguments);
                }
                throw new RuntimeException("All Dafny type references are NameSegments, since we do not use Dafny modules");
            }
            case JCTree.JCNewClass newClass -> {
                return translateNewRecord(origin, newClass);
            }
            case null, default -> {
            }
        }
        compiler.reportError(expr, "notSupported", expr.getClass().getSimpleName());
        return JavaToDafnyCompiler.getHole(origin);
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
            return JavaToDafnyCompiler.getHole(origin);
        }
        BinaryExprOpcode dafnyOperator = toDafny(operator);
        if (dafnyOperator == null) {
            compiler.reportError(node, "notSupported", "operator " + operator);
            return JavaToDafnyCompiler.getHole(origin);
        }
        return new BinaryExpr(origin, dafnyOperator, left, right);
    }

    /**
     * Translates the specified library method invocation to a Dafny expression,
     * or returns {@code null} if the invocation is not a JVerify library method.
     *
     * <p>Note: header methodContracts like {@link JVerify#precondition(boolean)}
     * and {@link JVerify#postcondition(boolean)}
     * must be translated by {@link BlockCompiler#translateStatement(JCTree.JCStatement)},
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
                    var paramType = compiler.translateType(param.getModifiers(), param.getType().type, paramOrigin);
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

    private Expression translateDynamicMethod(IOrigin origin, JCTree source, Symbol.DynamicMethodSymbol dynamicMethodSymbol) {
        //
        // invokedynamic in general is an invocation of a given "bootstrap method handle",
        // with a subset of the arguments provided statically.
        // javac translates lambda expressions and method references
        // to invokedynamic calls to java.lang.invoke.LambdaMetafactory.metafactory,
        // which is a method that creates factories of objects that implement single-method interfaces.
        // The static arguments in this case identify the target interface and
        // the synthetic static method that holds the lambda implementation.
        //
        // We can implement the same semantics
        // via a Dafny datatype that extends the equivalent trait
        // and a single data constructor that holds on to the static arguments
        // and prepends them to the arguments to the static method.
        //
        // E.g.:
        //
        // datatype Lambda42 extends SomethingDoer = Lambda42(p0: int, p1: int) {
        //   method doSomething(x: int, y: int) returns (r: int) {
        //     // doSomething$3 is a synthetic static method the UNLAMBDA phase extracted
        //     r := doSomething$3(p0, p1, x, y);
        //   }
        // }

        var types = Types.instance(compiler.context);
        var names = Names.instance(compiler.context);
        var maker = TreeMaker.instance(compiler.context).at(source.pos);
        var symtab = Symtab.instance(compiler.context);
        if (dynamicMethodSymbol.bsm.owner.type != symtab.lambdaMetafactory
              || dynamicMethodSymbol.bsm.name != names.metafactory) {
            compiler.reportError(source, "notSupported", "invokedynamic on " + dynamicMethodSymbol.bsm);
            return JavaToDafnyCompiler.getHole(compiler.toOrigin(source));
        }

        // Translate to a method declaration
        var interfaceType = dynamicMethodSymbol.dynamicType().getReturnType();
        var interfaceMethodSymbol = (Symbol.MethodSymbol) types.findDescriptorSymbol(interfaceType.tsym);
        com.sun.tools.javac.util.List<JCTree.JCVariableDecl> params = com.sun.tools.javac.util.List.nil();
        int index = 0;
        for (com.sun.tools.javac.code.Type pt : dynamicMethodSymbol.dynamicType().getParameterTypes()) {
            var name = names.fromString("p" + index);
            var symbol = new Symbol.VarSymbol(SYNTHETIC, name, pt, dynamicMethodSymbol);
            params = params.append(maker.VarDef(symbol, null));
            index++;
        }
        params = params.reverse();

        // See the signature and documentation of java.lang.invoke.LambdaMetafactory.metafactory.
        // We want the `MethodHandle implementation` parameter, which is in position 4 (with zero indexing)
        // but the first three parameters are filled in by the JVM, so it ends up being at index 1.
        var methodSymbol = (Symbol.MethodSymbol)((Symbol.MethodHandleSymbol)dynamicMethodSymbol.staticArgs[1]).baseSymbol();
        var arguments = params.<JCTree.JCExpression>map(p -> maker.Ident(p.sym)).appendList(interfaceMethodSymbol.params().map(p -> maker.Ident(p)));
        JCTree.JCExpression methodCall;
        if (JavaToDafnyCompiler.isConstructor(methodSymbol)) {
            var newClass = maker.NewClass(null, com.sun.tools.javac.util.List.nil(), maker.Type(methodSymbol.owner.type), arguments, null);
            newClass.constructor = methodSymbol;
            methodCall = newClass;
        } else {
            methodCall = methodSymbol.getModifiers().contains(Modifier.STATIC)
                    ? maker.App(maker.QualIdent(methodSymbol), arguments)
                    : maker.App(maker.Select(arguments.getFirst(), methodSymbol), arguments.tail);
        }
        var resultSymbol = new Symbol.VarSymbol(0, names.fromString("result"), methodSymbol.getReturnType(), dynamicMethodSymbol);
        var returnVar = maker.VarDef(maker.Modifiers(0), resultSymbol.name, maker.Type(methodSymbol.getReturnType()), methodCall);
        JCTree.JCStatement returnStmt = maker.Return(maker.Ident(resultSymbol));
        var stmts = com.sun.tools.javac.util.List.of(returnVar, returnStmt);
        var body = maker.Block(0, stmts);
        var contract = compiler.methodContracts.get(methodSymbol);
        var methodDecl = compiler.translateMethodOrLambda(source, maker.Modifiers(0), interfaceMethodSymbol, body, List.of(), contract);

        // Add a wrapper datatype with that method declaration to the outer scope
        var datatypeName = "Lambda" + compiler.declarationsForFile.get(compiler.compilationUnit).size();
        var datatypeNameNode = new Name(origin, datatypeName);
        List<Formal> datatypeCtorParams = params.stream().map(p ->
                new Formal(origin, compiler.getName(p, p.name), compiler.translateType(p.type, origin), false, true,
                        null, null, false, false, false, null)).toList();
        var datatypeCtor = new DatatypeCtor(origin, datatypeNameNode, null, false, datatypeCtorParams);
        var trait = compiler.translateType(interfaceType, origin);
        var datatypeDecl = new IndDatatypeDecl(origin, datatypeNameNode, null, List.of(), List.of(methodDecl),
                List.of(trait), List.of(datatypeCtor), false);
        compiler.declarationsForFile.get(compiler.compilationUnit).add(datatypeDecl);

        // Produce the datatype constructor reference: LambdaX.LambdaX
        return new ExprDotName(origin, new NameSegment(origin, datatypeName, null), datatypeNameNode, null);
    }

    /**
     * Translates the given {@code new RecordType(...)} invocation into a {@link DatatypeValue}
     * that can be used in pure contexts.
     */
    DatatypeValue translateNewRecord(IOrigin origin, JCTree.JCNewClass newClass) {
        var argBindings = newClass.getArguments().stream()
                .map(a -> new ActualBinding(null, toExpr(a), false)).toList();
        var datatypeName = compiler.getNameCompiler().getCompiledName(newClass.type.asElement());
        return new DatatypeValue(
                origin, datatypeName, datatypeName,
                new ActualBindings(argBindings));
    }
}
