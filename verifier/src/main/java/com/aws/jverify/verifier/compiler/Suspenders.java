package com.aws.jverify.verifier.compiler;

import com.sun.source.tree.CaseTree;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.comp.Operators;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.sun.tools.javac.code.Flags.NATIVE;
import static com.sun.tools.javac.code.Flags.RECORD;
import static com.sun.tools.javac.code.Flags.SYNTHETIC;


/**
 * A reversible rewriting phase that prevents features like
 * records and switch statements/expressions from being lowered by the LOWER phase.
 * (get it? :)
 */
public class Suspenders extends TreeTranslator {

    protected static final Context.Key<Suspenders> key = new Context.Key<>();

    private final Set<Symbol> records = new HashSet<>();

    private final Context context;
    private final TreeMaker maker;
    private final Names names;
    private final Name placeholderName;
    private JCTree.JCVariableDecl placeholderField;
    private final Symtab symtab;
    private final Name caseMatchName;

    public static Suspenders instance(Context context) {
        Suspenders instance = context.get(key);
        if (instance == null)
            instance = new Suspenders(context);
        return instance;
    }

    public Suspenders(Context context) {
        context.put(key, this);

        this.context = context;
        this.maker = TreeMaker.instance(context);
        this.names = Names.instance(context);
        this.placeholderName = names.fromString("$jverifySwitches");
        this.symtab = Symtab.instance(context);
        this.caseMatchName = names.fromString("$caseMatch");
    }

    private JCTree.JCExpression getPlaceholderField(JCTree.JCMethodInvocation invocation) {
        if (placeholderField == null) {
            var symtab = Symtab.instance(context);
            var owner = ((JCTree.JCIdent)invocation.getMethodSelect()).sym.owner;
            var symbol = new Symbol.VarSymbol(SYNTHETIC | NATIVE, placeholderName, symtab.booleanType, owner);
            this.placeholderField = maker.VarDef(symbol, maker.Literal(false));
        }
        return maker.Ident(placeholderField);
    }

    private enum Direction {
        HIDE,
        REVEAL
    }
    private Direction direction;

    public <T extends JCTree> T hide(T tree) {
        direction = Direction.HIDE;
        T result = translate(tree);
        direction = null;
        return result;
    }

    public <T extends JCTree> T reveal(T tree) {
        direction = Direction.REVEAL;
        T result = translate(tree);
        direction = null;
        return result;
    }

    @Override
    public void visitClassDef(JCTree.JCClassDecl tree) {
        switch (direction) {
            case HIDE -> {
                if ((tree.mods.flags & RECORD) != 0) {
                    records.add(tree.sym);
                    tree.mods.flags &= ~RECORD;
                }
            }
            case Direction.REVEAL -> {
                if (records.contains(tree.sym)) {
                    tree.mods.flags |= RECORD;
                }
            }
        }
        super.visitClassDef(tree);
    }

    @Override
    public void visitSwitch(JCTree.JCSwitch tree) {
        if (direction == Direction.HIDE) {
            var selector = translate(tree.selector);
            var cases = translateCases(tree.cases);
            result = switchToIf(selector, cases);
        } else {
            super.visitSwitch(tree);
        }
    }

    @Override
    public void visitIf(JCTree.JCIf tree) {
        if (direction == Direction.REVEAL) {
            var maybeSwitch = switchFromIf(tree);
            if (maybeSwitch.isPresent()) {
                result = maybeSwitch.get();
                result.accept(this);
                return;
            }
        }

        super.visitIf(tree);
    }

    @Override
    public void visitConditional(JCTree.JCConditional tree) {
        if (direction == Direction.REVEAL) {
            var maybeSwitch = switchExpressionFromConditional(tree);
            if (maybeSwitch.isPresent()) {
                result = maybeSwitch.get();
                result.accept(this);
                return;
            }
        }

        super.visitConditional(tree);
    }

    private JCTree.JCStatement switchToIf(JCTree.JCExpression selector, List<JCTree.JCCase> cases) {
        var rest = cases.tail.nonEmpty() ? switchToIf(selector, cases.tail) : null;
        return maker.If(caseToExpression(selector, cases.head, cases.head.labels), maker.Block(0, cases.head.stats), rest);
    }

    private Optional<JCTree.JCSwitch> switchFromIf(JCTree.JCIf ifStmt) {
        var maybeSelector = selectorFromIf(ifStmt);
        return maybeSelector.map(selector -> {
            var cases = casesFromIf(ifStmt);
            return maker.Switch(selector, cases);
        });
    }

    private List<JCTree.JCCase> casesFromIf(JCTree.JCIf ifStmt) {
        var labels = caseLabelsFromExpression(ifStmt.cond);
        var cas = maker.Case(CaseTree.CaseKind.RULE, labels, null, ((JCTree.JCBlock)ifStmt.thenpart).stats, ifStmt.thenpart);
        var ifCase = List.of(cas);
        if (ifStmt.elsepart instanceof JCTree.JCIf elseIf) {
            return ifCase.appendList(casesFromIf(elseIf));
        } else {
            return ifCase;
        }
    }

    private Optional<JCTree.JCExpression> selectorFromIf(JCTree.JCIf ifStmt) {
        var selector = ifStmt.elsepart instanceof JCTree.JCIf elseIf
            ? Stream.concat(selectorsFromExpression(ifStmt.cond), selectorsFromExpression(elseIf.cond))
            : selectorsFromExpression(ifStmt.cond);
        return selector.findFirst();
    }

    private Stream<JCTree.JCExpression> selectorsFromExpression(JCTree.JCExpression expr) {
        return switch (expr) {
            case JCTree.JCBinary binary -> {
                if (binary.operator.name.equals(caseMatchName)) {
                    yield Stream.of(binary.lhs);
                } else if (binary.hasTag(JCTree.Tag.OR)) {
                    yield Stream.concat(selectorsFromExpression(binary.lhs), selectorsFromExpression(binary.rhs));
                } else {
                    yield Stream.empty();
                }
            }
            default -> Stream.empty();
        };
    }

    private List<JCTree.JCCaseLabel> caseLabelsFromExpression(JCTree.JCExpression expr) {
        return switch (expr) {
            case JCTree.JCBinary binary -> {
                if (binary.operator.name.equals(caseMatchName)) {
                    yield List.of(maker.ConstantCaseLabel(binary.rhs));
                } else if (binary.hasTag(JCTree.Tag.OR)) {
                    yield caseLabelsFromExpression(binary.lhs).appendList(caseLabelsFromExpression(binary.rhs));
                } else {
                    throw new IllegalStateException();
                }
            }
            case JCTree.JCLiteral literal when literal.value == Boolean.TRUE ->
                List.of(maker.DefaultCaseLabel());
            default -> throw new IllegalStateException();
        };
    }

    private JCTree.JCExpression caseLabelToExpression(JCTree.JCExpression selector, JCTree.JCCaseLabel label) {
        return switch (label) {
            case JCTree.JCConstantCaseLabel constantCaseLabel -> {
                var methodType = new Type.MethodType(List.of(selector.type, constantCaseLabel.expr.type), symtab.booleanType, null, null);
                var result = makeResolvedBinary(JCTree.Tag.EQ, selector, constantCaseLabel.expr, methodType);
                result.operator = new Symbol.OperatorSymbol(caseMatchName, methodType, -1, symtab.noSymbol);;
                yield result;
            }
            case JCTree.JCDefaultCaseLabel _ -> maker.Literal(true);
            default -> throw new UnsupportedOperationException();
        };
    }

    private JCTree.JCExpression caseToExpression(JCTree.JCExpression selector, JCTree.JCCase cas, List<JCTree.JCCaseLabel> labels) {
        if (cas.caseKind == CaseTree.CaseKind.STATEMENT || cas.guard != null) {
            throw new UnsupportedOperationException();
        } else if (labels.tail.isEmpty()) {
            return caseLabelToExpression(selector, labels.head);
        } else {
            var rest = caseToExpression(selector, cas, labels.tail);
            return makeResolvedBinary(JCTree.Tag.OR, caseLabelToExpression(selector, labels.head), rest,
                    new Type.MethodType(List.of(symtab.booleanType, symtab.booleanType), symtab.booleanType, null, null));
        }
    }

    private JCTree.JCBinary makeResolvedBinary(JCTree.Tag opcode, JCTree.JCExpression lhs, JCTree.JCExpression rhs, Type.MethodType resultType) {
        var result = maker.Binary(opcode, lhs, rhs);
        result.operator = new Symbol.OperatorSymbol(names.equals, resultType, -1, symtab.noSymbol);
        result.type = resultType.restype;
        return result;
    }

    @Override
    public void visitSwitchExpression(JCTree.JCSwitchExpression tree) {
        if (direction == Direction.HIDE) {
            var selector = translate(tree.selector);
            var cases = translateCases(tree.cases);
            result = switchExpressionToConditional(selector, cases);
        } else {
            super.visitSwitchExpression(tree);
        }
    }

    private JCTree.JCExpression switchExpressionToConditional(JCTree.JCExpression selector, List<JCTree.JCCase> cases) {
        if (cases.isEmpty()) {
            // TODO: Should be a "hole" instead, or we should ensure the default case comes last in the list
            return maker.Literal(false);
        } else {
            var rest = switchExpressionToConditional(selector, cases.tail);
            var headBody = (JCTree.JCExpression) cases.head.body;
            var conditional = maker.Conditional(caseToExpression(selector, cases.head, cases.head.labels), headBody, rest);
            conditional.type = headBody.type;
            return conditional;
        }
    }

    private Optional<JCTree.JCSwitchExpression> switchExpressionFromConditional(JCTree.JCConditional conditional) {
        var maybeSelector = selectorsFromConditional(conditional).findFirst();
        return maybeSelector.map(selector -> {
            var cases = casesFromConditional(conditional);
            return maker.SwitchExpression(selector, cases);
        });
    }

    private Stream<JCTree.JCExpression> selectorsFromConditional(JCTree.JCConditional conditional) {
        return conditional.falsepart instanceof JCTree.JCConditional falseCond
                ? Stream.concat(selectorsFromExpression(conditional.cond), selectorsFromConditional(falseCond))
                : selectorsFromExpression(conditional.cond);
    }

    private List<JCTree.JCCase> casesFromConditional(JCTree.JCConditional conditional) {
        var labels = caseLabelsFromExpression(conditional.cond);
        var stats = List.<JCTree.JCStatement>of(maker.Yield(conditional.truepart));
        var cas = maker.Case(CaseTree.CaseKind.RULE, labels, null, stats, conditional.truepart);
        var truepartCase = List.of(cas);
        if (conditional.falsepart instanceof JCTree.JCConditional falseCond) {
            return truepartCase.appendList(casesFromConditional(falseCond));
        } else {
            return truepartCase;
        }
    }
}
