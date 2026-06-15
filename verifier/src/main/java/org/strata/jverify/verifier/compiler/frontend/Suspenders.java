package org.strata.jverify.verifier.compiler.frontend;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;

import java.util.HashSet;
import java.util.Set;

import static com.sun.tools.javac.code.Flags.RECORD;


///
/// A reversible rewriting phase that prevents features from being lowered
/// by the LOWER phase. (get it? :)
///
/// Currently, it applies two symmetric transformations to defend tree
/// shapes LOWER would otherwise mutate:
///
///   - Records: RECORD flag is removed before LOWER and restored after.
///     Without this LOWER would translate records into ordinary classes
///     with synthetic accessors etc., losing record-ness for downstream phases.
///   - Asserts: rewritten as `if ($assertMarker) { cond; }` before LOWER and
///     restored after. Without this LOWER would convert `assert` to `throw`.
///
/// One-way pre-LOWER transformations (e.g. switch desugaring) live in
/// their own classes; see SwitchDesugarer.
///
public class Suspenders extends TreeTranslator {

    protected static final Context.Key<Suspenders> key = new Context.Key<>();

    private final Set<Symbol> records = new HashSet<>();

    private final TreeMaker maker;
    private final Names names;
    private final Symtab symtab;

    public static Suspenders instance(Context context) {
        Suspenders instance = context.get(key);
        if (instance == null)
            instance = new Suspenders(context);
        return instance;
    }

    public Suspenders(Context context) {
        context.put(key, this);

        this.maker = TreeMaker.instance(context);
        this.names = Names.instance(context);
        this.symtab = Symtab.instance(context);
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
        }

        super.visitIf(tree);
    }
}
