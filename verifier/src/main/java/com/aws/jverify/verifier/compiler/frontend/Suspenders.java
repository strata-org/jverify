package com.aws.jverify.verifier.compiler.frontend;

import com.sun.source.tree.CaseTree;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static com.sun.tools.javac.code.Flags.RECORD;


///
/// A reversible rewriting phase that prevents features like
/// records and switch statements/expressions from being lowered by the LOWER phase.
/// (get it? :)
///
/// Currently, it applies two different transformations to avoid undesirable lowerings:
///
///   - Records are translated into normal classes with extra synthetic declarations.
///     To avoid this we remove the RECORD flag on records and then restore it afterwards.
///   - Switches with case rules (which we do support) are rewritten into case statements (which we don't).
///     To avoid this we rewrite switches into equivalent chained if statements or ?: conditionals,
///     and then reverse it afterwards.
///     This ensures all the content of the switches is still in scope for lowering.
///     See `switchToIf` and `switchExpressionToConditional` for details on the encoding.
///
public class Suspenders extends TreeTranslator {

    protected static final Context.Key<Suspenders> key = new Context.Key<>();

    private final Set<Symbol> records = new HashSet<>();

    private final Log log;
    private final TreeMaker maker;
    private final Names names;
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

        this.log = Log.instance(context);
        this.maker = TreeMaker.instance(context);
        this.names = Names.instance(context);
        this.symtab = Symtab.instance(context);
        this.caseMatchName = names.fromString("$caseMatch");
        this.assertMarkerName = names.fromString("$assertMarker");
    }

    private enum Direction {
        SUSPEND,
        UNSUSPEND
    }
    private Direction direction;
    
    // Name used to mark suspended assert statements
    private final Name assertMarkerName;

    public <T extends JCTree> T suspend(T tree) {
        direction = Direction.SUSPEND;
        T result = translate(tree);
        direction = null;
        return result;
    }

    public <T extends JCTree> T unsuspend(T tree) {
        direction = Direction.UNSUSPEND;
        T result = translate(tree);
        direction = null;
        return result;
    }

    @Override
    public void visitClassDef(JCTree.JCClassDecl tree) {
        switch (direction) {
            case SUSPEND -> {
                if ((tree.mods.flags & RECORD) != 0) {
                    records.add(tree.sym);
                    tree.mods.flags &= ~RECORD;
                }
            }
            case Direction.UNSUSPEND -> {
                if (records.contains(tree.sym)) {
                    tree.mods.flags |= RECORD;
                }
            }
        }
        super.visitClassDef(tree);
    }

    ///
    /// Transforms assert statements to prevent LOWER from converting them to throw statements.
    /// In SUSPEND mode: assert condition; -> if ($assertMarker) { condition; }
    /// In UNSUSPEND mode: if ($assertMarker) { condition; } -> assert condition;
    ///
    @Override
    public void visitAssert(JCTree.JCAssert tree) {
        if (direction == Direction.SUSPEND) {
            // Transform: assert condition; 
            // Into: if ($assertMarker) { condition; }
            // The marker condition prevents LOWER from recognizing this as an assert
            
            // Create a synthetic variable symbol for the marker
            var markerSymbol = new Symbol.VarSymbol(
                0, // flags
                assertMarkerName,
                symtab.booleanType,
                symtab.noSymbol // owner
            );
            
            var markerCondition = maker.Ident(markerSymbol);
            markerCondition.type = symtab.booleanType;
            
            var conditionStatement = maker.Exec(tree.cond);
            var thenBlock = maker.Block(0, List.of(conditionStatement));
            
            var ifStmt = maker.If(markerCondition, thenBlock, null);
            result = ifStmt;
            result.accept(this);
            return;
        }
        super.visitAssert(tree);
    }

    @Override
    public void visitIf(JCTree.JCIf tree) {
        if (direction == Direction.UNSUSPEND) {
            // Check if this is a suspended assert statement
            if (tree.cond instanceof JCTree.JCIdent ident && 
                ident.name.equals(assertMarkerName) &&
                tree.elsepart == null &&
                tree.thenpart instanceof JCTree.JCBlock block &&
                block.stats.size() == 1 &&
                block.stats.head instanceof JCTree.JCExpressionStatement exprStmt) {
                
                // Transform back: if ($assertMarker) { condition; } -> assert condition;
                var assertStmt = maker.Assert(exprStmt.expr, null);
                result = assertStmt;
                result.accept(this);
                return;
            }
            
            // Check if this is a switch-encoded if statement
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
    public void visitSwitch(JCTree.JCSwitch tree) {
        if (direction == Direction.SUSPEND) {
            var exhaustive = tree.isExhaustive &&
                    tree.cases.stream().noneMatch(cas ->
                            cas.labels.stream().anyMatch(label -> label instanceof JCTree.JCDefaultCaseLabel));
            var maybeIf = switchToIf(tree.selector, tree.cases, exhaustive);
            if (maybeIf.isPresent()) {
                result = maybeIf.get();
                result.accept(this);
                return;
            }
        }

        super.visitSwitch(tree);
    }

    @Override
    public void visitConditional(JCTree.JCConditional tree) {
        if (direction == Direction.UNSUSPEND) {
            var maybeSwitch = switchExpressionFromConditional(tree);
            if (maybeSwitch.isPresent()) {
                result = maybeSwitch.get();
                result.accept(this);
                return;
            }
        }

        super.visitConditional(tree);
    }

    ///
    /// Translates a switch statement into an equivalent chained if statement.
    /// See also switchFromIf for the reverse transformation used
    /// in the UNSUSPECT direction.
    ///
    /// For example:
    /// {@snippet :
    ///       var num = -1;
    ///       switch (i) {
    ///            case 0 -> num = 10;
    ///            case 1, 2 -> num = 20;
    ///            case 3 -> num = 30;
    ///            default -> num = 40;
    ///       }
    ///  }
    /// Becomes:
    /// {@snippet :
    ///       var num = -1;
    ///       if (i == 0) {
    ///            num = 10;
    ///       } else if (i == 1 || i == 2) {
    ///            num = 20;
    ///       } else if (i == 3) {
    ///            num = 30;
    ///       } else {
    ///            num = 40;
    ///       }
    ///  }
    /// If the switch was determined to be exhaustive and there is no default case label,
    /// then an extra `else { assert false; } branch is added at the end to encode that bit.
    ///
    /// The "==" operators are tagged with a separate Name to identify them
    /// as encoding implicit switch case patterns,
    /// so there is no ambiguity with existing source code.
    ///
    /// Returns an Optional<JCStatement> because not all switch statements are possible
    /// to encode in this way. This mapping covers all statements we currently support,
    /// however, so if the encoding fails we can just skip suspending the switch.
    /// The translation will cause errors anyway,
    /// and the only consequence of the LOWER rewriting is that
    /// some errors may be relocated or duplicated.
    ///
    private Optional<JCTree.JCStatement> switchToIf(JCTree.JCExpression selector, List<JCTree.JCCase> cases, boolean exhaustive) {
        final JCTree.JCStatement rest;
        if (cases.tail.nonEmpty()) {
            var maybeRest = switchToIf(selector, cases.tail, exhaustive);
            if (maybeRest.isEmpty()) {
                return maybeRest;
            }
            rest = maybeRest.get();
        } else if (exhaustive) {
            maker.pos = selector.pos;
            rest = maker.Assert(maker.Literal(false), null);
        } else {
            rest = null;
        }
        return caseToExpression(selector, cases.head, cases.head.labels).map(head -> {
            maker.pos = head.pos;
            return maker.If(head, maker.Block(0, cases.head.stats), rest);
        });
    }

    private Optional<JCTree.JCSwitch> switchFromIf(JCTree.JCIf ifStmt) {
        var maybeSelector = selectorFromIf(ifStmt);
        return maybeSelector.map(selector -> {
            var cases = casesFromIf(ifStmt);
            var result = maker.Switch(selector, cases);
            result.isExhaustive = exhaustiveFromIf(ifStmt);
            return result;
        });
    }

    private List<JCTree.JCCase> casesFromIf(JCTree.JCIf ifStmt) {
        var labels = caseLabelsFromExpression(ifStmt.cond);
        var cas = maker.Case(CaseTree.CaseKind.RULE, labels, null, ((JCTree.JCBlock)ifStmt.thenpart).stats, ifStmt.thenpart);
        var ifCase = List.of(cas);
        if (ifStmt.elsepart instanceof JCTree.JCIf elseIf) {
            return ifCase.appendList(casesFromIf(elseIf));
        } else if (ifStmt.elsepart != null) {
            return ifCase.append(defaultCase(ifStmt.elsepart));
        } else {
            return ifCase;
        }
    }

    private JCTree.JCCase defaultCase(JCTree.JCStatement statement) {
        return maker.Case(CaseTree.CaseKind.RULE, List.of(maker.DefaultCaseLabel()), null, ((JCTree.JCBlock)statement).stats, statement);
    }

    private boolean exhaustiveFromIf(JCTree.JCIf ifStmt) {
        if (ifStmt.elsepart instanceof JCTree.JCIf elseIf) {
            return exhaustiveFromIf(elseIf);
        } else {
            return ifStmt.elsepart != null;
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

    private Optional<JCTree.JCExpression> caseLabelToExpression(JCTree.JCExpression selector, JCTree.JCCaseLabel label) {
        return switch (label) {
            case JCTree.JCConstantCaseLabel constantCaseLabel -> {
                var methodType = new Type.MethodType(List.of(selector.type, constantCaseLabel.expr.type), symtab.booleanType, null, null);
                var result = makeResolvedBinary(JCTree.Tag.EQ, selector, constantCaseLabel.expr, methodType);
                result.operator = new Symbol.OperatorSymbol(caseMatchName, methodType, -1, symtab.noSymbol);;
                yield Optional.of(result);
            }
            case JCTree.JCDefaultCaseLabel _ -> Optional.of(maker.Literal(true));
            default -> Optional.empty();
        };
    }

    private Optional<JCTree.JCExpression> caseToExpression(JCTree.JCExpression selector, JCTree.JCCase cas, List<JCTree.JCCaseLabel> labels) {
        if (cas.caseKind == CaseTree.CaseKind.STATEMENT) {
            return Optional.empty();
        } else if (labels.tail.isEmpty()) {
            return caseLabelToExpression(selector, labels.head);
        } else {
            return caseToExpression(selector, cas, labels.tail).flatMap(rest ->
                caseLabelToExpression(selector, labels.head).map(head ->
                    makeResolvedBinary(JCTree.Tag.OR, head, rest,
                            new Type.MethodType(List.of(symtab.booleanType, symtab.booleanType), symtab.booleanType, null, null))));
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
        if (direction == Direction.SUSPEND) {
            var maybeSwitch = switchExpressionToConditional(tree.selector, tree.cases, tree.type);
            if (maybeSwitch.isPresent()) {
                result = maybeSwitch.get();
                result.accept(this);
                return;
            }
        }

        super.visitSwitchExpression(tree);
    }

    ///
    /// Translates a switch expression into an equivalent chained conditional expression.
    /// See also switchExpressionFromConditional for the reverse transformation used
    /// in the UNSUSPEND direction.
    ///
    /// For example:
    /// {@snippet :
    ///       var num = switch (i) {
    ///            case 0 -> 10;
    ///            case 1, 2 -> 20;
    ///            case 3 -> 30;
    ///            default -> 40;
    ///       };
    ///  }
    /// Becomes:
    /// {@snippet :
    ///       var num = (i == 10) ? 10 :
    ///            (i == 1 || i == 2) ? 20 :
    ///                 (i == 3) ? 30 :
    ///                      (true) ? 40 : <hole>
    ///  }
    /// The "==" operators are tagged with a separate Name to identify them
    /// as encoding implicit switch case patterns,
    /// so there is no ambiguity with existing source code.
    ///
    /// Returns an Optional<JCExpression> because not all switch expressions are possible
    /// to encode in this way. This mapping covers all statements we currently support,
    /// however, so if the encoding fails we can just skip suspending the switch.
    /// The translation will cause errors anyway,
    /// and the only consequence of the LOWER rewriting is that
    /// some errors may be relocated or duplicated.
    ///
    private Optional<JCTree.JCExpression> switchExpressionToConditional(JCTree.JCExpression selector, List<JCTree.JCCase> cases, Type typ) {
        if (cases.isEmpty()) {
            // TODO: Should be a "hole" instead, or we should ensure the default case comes last in the list
            return Optional.of(maker.Literal(false));
        } else {
            var rest = switchExpressionToConditional(selector, cases.tail, typ);
            if (rest.isEmpty()) {
                return rest;
            }

            if (cases.head.body instanceof JCTree.JCExpression headExpr) {
                return caseToExpression(selector, cases.head, cases.head.labels).map(head -> {
                    var conditional = maker.Conditional(head, headExpr, rest.get());
                    conditional.type = typ;
                    return conditional;
                });
            } else {
                return Optional.empty();
            }

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
            return truepartCase.append(defaultCaseFromExpr(conditional.falsepart));
        }
    }

    private JCTree.JCCase defaultCaseFromExpr(JCTree.JCExpression expr) {
        var stats = List.<JCTree.JCStatement>of(maker.Yield(expr));
        return maker.Case(CaseTree.CaseKind.RULE, List.of(maker.DefaultCaseLabel()), null, stats, expr);
    }
}
