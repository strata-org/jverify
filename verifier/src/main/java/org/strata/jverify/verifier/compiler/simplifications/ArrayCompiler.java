package org.strata.jverify.verifier.compiler.simplifications;

import org.strata.jverify.verifier.compiler.Reporter;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.model.JavacElements;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Names;

import java.util.Set;

public class ArrayCompiler extends TreeTranslator {
    public static final String COM_AWS_JVERIFY_BUILTIN_JARRAY = "org.strata.jverify.builtin.JArray";
    private final Symbol.ClassSymbol arraySymbol;
    private final Symbol.MethodSymbol getMethodSymbol;
    private final Symbol.MethodSymbol createMethodSymbol;
    private final Symbol.MethodSymbol setMethodSymbol;
    private final Reporter reporter;
    private final TreeMaker maker;
    private final JavacElements elements;
    private final Names names;
    private final com.sun.tools.javac.code.Symtab symtab;

    public java.util.List<JCTree.JCCompilationUnit> transform(java.util.List<JCTree.JCCompilationUnit> envs) {
        for (var env : envs) {
            translate(env);
        }
        return envs;
    }
    
    public ArrayCompiler(Context context) {
        this.maker = TreeMaker.instance(context);
        this.elements = JavacElements.instance(context);
        this.names = Names.instance(context);
        this.symtab = com.sun.tools.javac.code.Symtab.instance(context);
        this.reporter = Reporter.instance(context);

        arraySymbol = elements.getTypeElement(COM_AWS_JVERIFY_BUILTIN_JARRAY);
        getMethodSymbol = (Symbol.MethodSymbol)arraySymbol.members().getSymbolsByName(names.fromString("get")).iterator().next();
        createMethodSymbol = (Symbol.MethodSymbol)arraySymbol.members().getSymbolsByName(names.fromString("create")).iterator().next();
        setMethodSymbol = (Symbol.MethodSymbol)arraySymbol.members().getSymbolsByName(names.fromString("set")).iterator().next();
    }

    @Override
    public void visitTopLevel(JCTree.JCCompilationUnit tree) {
        reporter.compilationUnit = tree;
        super.visitTopLevel(tree);
    }

    @Override
    public void visitNewClass(JCTree.JCNewClass tree) {
        if (tree.type instanceof com.sun.tools.javac.code.Type.ArrayType arrayType) {
            maker.pos = tree.pos;
            JCTree.JCMethodInvocation create = maker.App(maker.Select(maker.Ident(arraySymbol), createMethodSymbol), tree.args);
            create.typeargs = List.of(maker.Type(arrayType.elemtype));
            result = create;
            result.type = tree.type;
        } else {
            super.visitNewClass(tree);
        }
    }

    @Override
    public void visitAssign(JCTree.JCAssign tree) {
        if (tree.lhs instanceof JCTree.JCArrayAccess arrayAccess) {
            maker.pos = tree.pos;
            var arrayExpr = arrayAccess.getExpression();
            tree.rhs = translate(tree.rhs);

            if (arrayExpr instanceof JCTree.JCIdent ident) {
                // `arr[i] = v` lowers to the pure map-store `arr.set(i, v)`
                // (modelled in Laurel as arraySet, which returns a fresh
                // map). To make the write observable by later reads we
                // rebind the array variable: `arr = arr.set(i, v)`.
                var setCall = maker.App(
                        maker.Select(maker.Ident(ident.sym), setMethodSymbol),
                        List.of(arrayAccess.getIndex(), tree.rhs));
                setCall.type = arrayExpr.type;
                var rebind = maker.Assign(maker.Ident(ident.sym), setCall);
                rebind.type = arrayExpr.type;
                result = rebind;
            } else {
                // We can only rebind a simple (assignable) local-variable
                // array. For any other array expression — a field access,
                // a method-call result, a nested index, etc. — we cannot
                // make the store observable, so reject it with a clear
                // error rather than silently dropping the write (which
                // would mis-model the program).
                reporter.reportError(tree, "notSupported",
                        "array element assignment whose array is not a simple local variable");
                var setCall = maker.App(
                        maker.Select(arrayExpr, setMethodSymbol),
                        List.of(arrayAccess.getIndex(), tree.rhs));
                setCall.type = tree.type;
                result = setCall;
            }
        }
        else {
            super.visitAssign(tree);
        }

    }

    @Override
    public void visitAnnotation(JCTree.JCAnnotation tree) {
        result = tree;
    }

    @Override
    public void visitClassDef(JCTree.JCClassDecl tree) {
        if (tree.sym.isEnum()) {
            result = tree;
        } else {
            super.visitClassDef(tree);
        }
    }

    @Override
    public void visitNewArray(JCTree.JCNewArray newArray) {
        super.visitNewArray(newArray);
        
        var elementType = ((com.sun.tools.javac.code.Type.ArrayType) newArray.type).elemtype;
        if (elementType instanceof com.sun.tools.javac.code.Type.ArrayType) {
            reporter.reportError(newArray, "notSupported", "multi-dimensional arrays");
        }

        maker.pos = newArray.pos;
        JCTree.JCExpression size;
        if (newArray.getInitializers() != null && !newArray.getInitializers().isEmpty()) {
            // Initializer-list form `{v0, v1, ...}` (or
            // `new int[]{v0, ...}`): we lower this to
            // `JArray.create(N)` where N is the literal length.
            // The contents are then unconstrained — Strata sees a
            // fresh array of the right length but nondet entries.
            // This is coarse but lets verification proceed; users
            // who rely on the literal contents (rather than just
            // bounds-style reasoning) will need to add explicit
            // assumptions or wait for a per-arity arrayInit_N
            // axiomatization on the JavaToLaurelCompiler side.
            JCTree.JCLiteral sizeLit =
                maker.Literal(newArray.getInitializers().size());
            // Set the literal's type so downstream attribution
            // (line-map resolution, etc.) doesn't trip.
            sizeLit.type = symtab.intType;
            size = sizeLit;
        } else {
            size = newArray.getDimensions().head;
        }

        JCTree.JCMethodInvocation create = maker.App(maker.Select(maker.Ident(arraySymbol), createMethodSymbol), 
                List.of(size));
        create.typeargs = List.of(maker.Type(elementType));
        result = create;
        result.type = newArray.type;
    }

    @Override
    public void visitIndexed(JCTree.JCArrayAccess tree) {
        super.visitIndexed(tree);
        
        maker.pos = tree.pos;
        result = maker.App(maker.Select(tree.getExpression(), getMethodSymbol), List.of(tree.getIndex()));
        result.type = tree.type;
    }


}
