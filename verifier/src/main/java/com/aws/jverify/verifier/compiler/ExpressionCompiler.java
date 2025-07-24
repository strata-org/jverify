package com.aws.jverify.verifier.compiler;

import com.aws.jverify.Contract;
import com.aws.jverify.Modifiable;
import com.aws.jverify.generated.*;
import com.aws.jverify.verifier.compiler.simplifications.JVerifyGhostExpressionCompiler;
import com.aws.jverify.verifier.compiler.simplifications.RecordCompiler;
import com.sun.source.tree.Tree;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.TypeTag;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import org.checkerframework.checker.nullness.qual.Nullable;

import javax.lang.model.type.ArrayType;
import javax.lang.model.type.TypeKind;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class ExpressionCompiler {
     public final JavaToDafnyCompiler compiler;

     private static final Set<String> supportedStringMethods = Set.of("equals", "concat", "startsWith", "substring", "isEmpty", "charAt", "length", "indexOf");

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
            if (body == null) {
                // This only happens for statement labels, which would have already raised an error in translateSwitchLabels
                translatedBody = JavaToDafnyCompiler.getHole(origin);
            } else if (body instanceof JCTree.JCExpression) {
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
                return translateConditional(conditional, origin);
            }
            case JCTree.JCSwitchExpression switchExpr -> {
                return translateSwitchExpression(switchExpr);
            }
            case JCTree.JCUnary unary -> {
                return translateUnary(expr, unary, origin);
            }
            case JCTree.JCBinary binary -> {
                return translateBinary(binary);
            }
            case JCTree.JCIdent identifier -> {
                return translateIdentifier(identifier, origin);
            }
            case JCTree.JCLiteral literal -> {
                return translateLiteral(expr, literal, origin);
            }
            case JCTree.JCMethodInvocation invocation -> {
                return translateMethodInvocation(invocation, origin);
            }
            case JCTree.JCFieldAccess fieldAccess -> {
                return translateFieldAccess(fieldAccess, origin);
            }
            case JCTree.JCArrayAccess arrayAccess -> {
                return translateArrayAccess(arrayAccess, origin);
            }
            case JCTree.JCParens parens -> {
                return toExpr(parens.getExpression());
            }
            case JCTree.JCAssignOp assignOp -> {
                compiler.reportError(expr, "mutatingExpression", assignOp.getOperator().name.toString() + "=");
                return JavaToDafnyCompiler.getHole(origin);
            }
            case JCTree.JCInstanceOf instanceOf -> {
                return translateInstanceOf(instanceOf, origin);
            }
            case JCTree.JCTypeCast cast -> {
                return translateCast(cast, origin);
            }
            case JCTree.JCLambda _ ->
                throw new RuntimeException("Lambdas should have been rewritten, but found one at " + origin);
            case JCTree.JCMemberReference _ ->
                throw new RuntimeException("Member references should have been rewritten, but found one at " + origin);
            case JCTree.JCTypeApply typeApply -> {
                return translateTypeApplication(typeApply, origin);
            }
            case JCTree.JCNewClass newClass -> {
                return translateNew(expr, newClass, origin);
            }
            default -> { }
        }
        compiler.reportError(expr, "notSupported", expr.getClass().getSimpleName() + " in an expression");
        return JavaToDafnyCompiler.getHole(origin);
    }

    private Expression translateNew(JCTree.JCExpression expr, JCTree.JCNewClass newClass, IOrigin origin) {
        Symbol.ClassSymbol classSymbol = (Symbol.ClassSymbol) newClass.type.tsym;
        if (useConstructorFunction(compiler, newClass, classSymbol)) {
            return RecordCompiler.translateNewRecord(this, origin, newClass);
        }
        compiler.reportError(expr, "notSupported",
                "using 'new' in an expression to create an instance of a non-record class");
        return JavaToDafnyCompiler.getHole(origin);
    }

    public static boolean useConstructorFunction(JavaToDafnyCompiler compiler,  JCTree.JCNewClass newClass, Symbol.ClassSymbol classSymbol) {
        return compiler.isRecord(newClass.type) || isImmutableClass(compiler, classSymbol);
    }

    private static boolean isImmutableClass(JavaToDafnyCompiler compiler, Symbol.ClassSymbol classSymbol) {
        var decls = compiler.externalContractCompiler.declarationsForSymbolContract.get(classSymbol);
        boolean immutableClass = false;
        if (decls.size() == 1) {
            var contract = decls.getFirst().sym.getAnnotation(Contract.class);
            if (contract != null) {
                immutableClass = contract.immutable();
            }
        }
        return immutableClass;
    }

    private TypeTestExpr translateInstanceOf(JCTree.JCInstanceOf instanceOf, IOrigin origin) {
        var expression = toExpr(instanceOf.getExpression());
        var jcType = compiler.translateType(instanceOf.getType());
        return new TypeTestExpr(origin, expression, jcType);
    }

    private ConversionExpr translateCast(JCTree.JCTypeCast cast, IOrigin origin) {
        var castExpr = toExpr(cast.getExpression());

        var type = compiler.translateType(cast);
        return new ConversionExpr(origin, castExpr, type, "");
    }

    private SeqSelectExpr translateArrayAccess(JCTree.JCArrayAccess arrayAccess, IOrigin origin) {
        var arrayExpr = toExpr(arrayAccess.getExpression());
        var indexExpr = toExpr(arrayAccess.getIndex());
        return new SeqSelectExpr(origin, true, arrayExpr, indexExpr, null, null);
    }

    private ITEExpr translateConditional(JCTree.JCConditional conditional, IOrigin origin) {
        var condition = toExpr(conditional.getCondition());
        var thenBranch = toExpr(conditional.getTrueExpression());
        var elseBranch = toExpr(conditional.getFalseExpression());
        return new ITEExpr(origin, false, condition, thenBranch, elseBranch);
    }

    private Expression translateUnary(JCTree.JCExpression expr, JCTree.JCUnary unary, IOrigin origin) {
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

    private Expression translateBinary(JCTree.JCBinary binary) {
        var left = toExpr(binary.getLeftOperand());
        var right = toExpr(binary.getRightOperand());
        Symbol.OperatorSymbol operator = binary.getOperator();
        return translateBinary(
                binary, binary.getLeftOperand().type, binary.getRightOperand().type,
                operator, left, right);
    }
    
    private java.util.function.BiFunction<JCTree.JCIdent, IOrigin, Expression> handleIdentifier;

    public <T> T withOverrideTranslateIdentifier(Supplier<T> supplier, 
                                                 java.util.function.BiFunction<JCTree.JCIdent, IOrigin, Expression> override) 
    {
        var previous = handleIdentifier;
        handleIdentifier = override;
        var result = supplier.get();
        handleIdentifier = previous;
        return result;
    }
    
    public Expression translateIdentifier(JCTree.JCIdent identifier, IOrigin origin) {
        if (handleIdentifier == null) {
            return translateIdentifierNoOverride(identifier, origin);
        } else {
            return handleIdentifier.apply(identifier, origin);
        }
    }
    
    public Expression translateIdentifierNoOverride(JCTree.JCIdent identifier, IOrigin origin) {
        var identName = compiler.nameCompiler.getCompiledName(identifier.sym);
        if (identName.contentEquals("this")) {
            return new ThisExpr(origin);
        }
        return new NameSegment(origin, identName, null);
    }

    private Expression translateLiteral(JCTree.JCExpression expr, JCTree.JCLiteral literal, IOrigin origin) {
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

    private NameSegment translateTypeApplication(JCTree.JCTypeApply typeApply, IOrigin origin) {
        var type = toExpr(typeApply.getType());
        if (type instanceof NameSegment nameSegment) {
            List<Type> arguments;
            if (typeApply.getTypeArguments().isEmpty()) {
                // Occurs when the type arguments were inferred
                arguments = typeApply.type.getTypeArguments().stream().map(t -> compiler.translateType(t, origin, null)).toList();
            } else {
                arguments = typeApply.getTypeArguments().stream().map(compiler::translateType).toList();
            }
            return new NameSegment(origin, nameSegment.getName(), arguments);
        }
        throw new RuntimeException("All Dafny type references are NameSegments, since we do not use Dafny modules");
    }

    private Expression translateFieldAccess(JCTree.JCFieldAccess fieldAccess, IOrigin origin) {
        if (fieldAccess.sym instanceof Symbol.ClassSymbol classSymbol) {
            return new NameSegment(origin, compiler.nameCompiler.getCompiledName(classSymbol), List.of());
        }
        if (fieldAccess.sym instanceof Symbol.DynamicMethodSymbol dynamicMethodSymbol) {
            return compiler.lambdaCompiler.translateDynamicMethod(origin, fieldAccess, dynamicMethodSymbol);
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
            boolean methodContainerTypeIsParameter = fieldAccess.selected.type instanceof com.sun.tools.javac.code.Type.TypeVar;
            if (methodContainerTypeIsParameter) {
                var classType = fieldAccess.sym.enclClass().type;
                // Dafny needs an explicit cast otherwise it won't find the members from the type parameter bounds
                selectedExpr = new ConversionExpr(origin, selectedExpr, compiler.translateType(classType, origin), "");
            }
            return new ExprDotName(origin, selectedExpr, compiler.getName(fieldAccess, fieldName), null);
        }
    }

    private Expression translateMethodInvocation(JCTree.JCMethodInvocation invocation, IOrigin origin) {
        var jverifyMethodExpr = new JVerifyGhostExpressionCompiler(this).jverifyLibMethodToExpr(invocation);
        if (jverifyMethodExpr != null) {
            return jverifyMethodExpr;
        }

        var methodSymbol = TreeInfo.symbol(invocation.getMethodSelect());
        var argBindings = invocation.getArguments().stream().map(a -> new ActualBinding(null, toExpr(a), false)).toList();

        if (!((invocation.getMethodSelect() instanceof JCTree.JCFieldAccess fieldAccess) ||
             (invocation.getMethodSelect() instanceof JCTree.JCIdent))) {
            compiler.reportError(invocation, "notSupported", "call via method reference");
        }

        if (methodSymbol.owner instanceof Symbol.ClassSymbol ownerClass
                && ownerClass.isRecord()
                && invocation.getArguments().isEmpty()
        ) {
            var component = ownerClass.getRecordComponents().stream()
                    .filter(comp -> comp.name.equals(methodSymbol.name))
                    .findAny();
            if (component.isPresent()) {
                final Expression receiver;
                if (invocation.getMethodSelect() instanceof JCTree.JCFieldAccess fieldAccess) {
                    receiver = toExpr(fieldAccess.selected);
                } else if (invocation.getMethodSelect() instanceof JCTree.JCIdent) {
                    receiver = null;
                } else {
                    receiver = JavaToDafnyCompiler.getHole(origin);
                }
                var fieldNameStr = compiler.nameCompiler.getCompiledName(component.get());
                var fieldName = compiler.getName(invocation.getMethodSelect(), fieldNameStr);
                return new ExprDotName(origin, receiver, fieldName, null);
            }
        }

        if (methodSymbol.owner instanceof Symbol.ClassSymbol ownerClass
                && ownerClass.fullname.contentEquals(String.class.getName())) {
                 if (!supportedStringMethods.contains(methodSymbol.name.toString())) {
                    compiler.reportError(invocation, "notSupported", "String method " + methodSymbol);
                    return compiler.getHole(origin);
                }
        }

        var target = toExpr(invocation.getMethodSelect());
        return new ApplySuffix(origin, target, null,
                new ActualBindings(argBindings), null);
    }

    /**
     * Translates a binary operator expression to Dafny.
     *
     * @param rightType must be non-null if the operator is a comparison
     */
    public Expression translateBinary(
            JCTree node,
            com.sun.tools.javac.code.Type leftType,
            com.sun.tools.javac.code.@Nullable Type rightType,
            Symbol.OperatorSymbol operator,
            Expression left,
            Expression right) {
        var origin = compiler.toOrigin(node);
        var opName = operator.name.toString();
        if (leftType.getTag() == TypeTag.FLOAT || leftType.getTag() == TypeTag.DOUBLE) {
            compiler.reportError(node, "notSupported", "operator " + operator);
        }

        if (opName.equals("+") && leftType.toString().contentEquals(String.class.getName())) {
            var callee = new ExprDotName(origin, left, new Name(origin, "concat"), null);
            var arg = List.of(new ActualBinding(null, right, false));
            return new ApplySuffix(origin, callee, null,
                    new ActualBindings(arg), null);
        }

        if (opName.equals("==") || opName.equals("!=")) {
            assert rightType != null;

            // Some Java reference types are translated to Dafny value types
            // (e.g. String translates to seq<char16>, record classes translate to datatypes);
            // let's call these "Java-Reference-as-Dafny-Value types", or "JRDV types" for short.
            // Distinct heap objects of JRDV type are certainly not equal according to Java's "==",
            // but their translated values could be (structurally) equal according to Dafny's "==",
            // and this difference in semantics could lead to unsoundness.
            var isSafe = false;

            // If either operand is definitely null,
            // then comparison to a value of JRDV type will be rejected during Dafny resolution,
            // because the translation of a JRDV type is a Dafny value type by definition.
            isSafe |= leftType.getKind() == TypeKind.NULL || rightType.getKind() == TypeKind.NULL;

            // If one operand is of primitive type,
            // then the other operand must either also be of primitive type,
            // or of a boxed type (in which case the operand undergoes unboxing promotion).
            // In both cases the Java semantics are preserved in translation.
            isSafe |= leftType.isPrimitive() || rightType.isPrimitive();

            // If one operand is definitely not of a JRDV type,
            // then since we already checked that it's not of primitive type,
            // we know that it's of reference type and will be translated to a Dafny reference type.
            // The type of the other operand doesn't matter:
            // if it's of JRDV type then the comparison will be rejected during Dafny resolution.
            isSafe |= !isPossiblyJrdvType(leftType) || !isPossiblyJrdvType(rightType);

            if (!isSafe) {
                compiler.reportError(node, "equalityOperatorRestricted", opName);
                return JavaToDafnyCompiler.getHole(origin);
            }
        }

        var isBitwise = switch (opName) {
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
     * Returns whether a value of the given type is possibly a "Java-Reference-as-Dafny-Value" type ("JRDV type").
     */
    private boolean isPossiblyJrdvType(com.sun.tools.javac.code.Type type) {
        if (type.isPrimitive()) {
            return false;
        }

        var symtab = Symtab.instance(this.compiler.context);
        if (type.baseType() == symtab.objectType) {
            return !compiler.isAnnotated(type, Modifiable.class);
        }

        var types = Types.instance(this.compiler.context);
        return Stream.concat(primitiveTypes().stream(), jrdvTypes().stream())
                .anyMatch(t -> types.isAssignable(type, t) || types.isAssignable(t, type));
    }

    private List<com.sun.tools.javac.code.Type> primitiveTypes() {
        var symtab = Symtab.instance(this.compiler.context);
        return List.of(
                symtab.booleanType,
                symtab.byteType,
                symtab.shortType,
                symtab.charType,
                symtab.intType,
                symtab.longType,
                symtab.floatType,
                symtab.doubleType
        );
    }

    /**
     * Returns a list of the "Java-Reference-as-Dafny-Value" types ("JRDV types").
     */
    private List<com.sun.tools.javac.code.Type> jrdvTypes() {
        var symtab = Symtab.instance(this.compiler.context);
        return List.of(symtab.stringType, symtab.recordType);
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
                return new ApplySuffix(origin, new NameSegment(origin, "JS", null), null,
                        new ActualBindings(List.of(new ActualBinding(null, new SeqDisplayExpr(origin, charExprs), false))), null);
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
