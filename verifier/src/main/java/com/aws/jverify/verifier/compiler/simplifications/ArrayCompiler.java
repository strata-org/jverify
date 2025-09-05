package com.aws.jverify.verifier.compiler.simplifications;

import com.aws.jverify.verifier.compiler.Reporter;
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
    public static final String COM_AWS_JVERIFY_BUILTIN_JARRAY = "com.aws.jverify.builtin.JArray";
    private final Symbol.ClassSymbol arraySymbol;
    private final Symbol.MethodSymbol getMethodSymbol;
    private final Symbol.MethodSymbol createMethodSymbol;
    private final Symbol.MethodSymbol setMethodSymbol;
    private final Reporter reporter;
    private final TreeMaker maker;
    private final JavacElements elements;
    private final Names names;

    public Set<JCTree.JCCompilationUnit> transform(Set<JCTree.JCCompilationUnit> envs) {
        for (var env : envs) {
            translate(env);
        }
        return envs;
    }
    
    public ArrayCompiler(Context context) {
        this.maker = TreeMaker.instance(context);
        this.elements = JavacElements.instance(context);
        this.names = Names.instance(context);
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
            result = maker.App(maker.Select(arrayAccess.getExpression(), setMethodSymbol), 
                    List.of(arrayAccess.getIndex(), tree.getExpression()));
            result.type = tree.type;
        } else {
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

        JCTree.JCExpression size;
        if (newArray.getInitializers() != null && !newArray.getInitializers().isEmpty()) {
            reporter.reportError(newArray, "notSupported", "new array with initializers");
            size = maker.Literal(0);
        } else {
            size = newArray.getDimensions().head;
        }

        maker.pos = newArray.pos;
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
