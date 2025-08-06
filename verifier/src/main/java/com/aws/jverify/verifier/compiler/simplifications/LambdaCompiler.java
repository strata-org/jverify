package com.aws.jverify.verifier.compiler.simplifications;

import com.aws.jverify.generated.*;
import com.aws.jverify.verifier.compiler.TypeDeclarationCompiler;
import com.aws.jverify.verifier.compiler.JavaToDafnyCompiler;
import com.aws.jverify.verifier.compiler.MethodOrLoopContract;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.Names;

import javax.lang.model.element.Modifier;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.sun.tools.javac.code.Flags.SYNTHETIC;

public class LambdaCompiler {
    JavaToDafnyCompiler compiler;

    public LambdaCompiler(JavaToDafnyCompiler compiler) {
        this.compiler = compiler;
    }

    // All contracts, internal or external
    public final Map<Symbol.MethodSymbol, MethodOrLoopContract> methodContracts = new HashMap<>();

    public Expression translateDynamicMethod(IOrigin origin, JCTree source, Symbol.DynamicMethodSymbol dynamicMethodSymbol) {
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
        JCTree.JCStatement returnStmt = maker.Return(methodCall);
        var stmts = com.sun.tools.javac.util.List.of(returnStmt);
        var body = maker.Block(0, stmts);
        var contract = methodContracts.get(methodSymbol);
        var methodDecl = new TypeDeclarationCompiler(compiler).translateMethodOrLambda(source, maker.Modifiers(0), interfaceMethodSymbol, body, List.of(), contract);

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
}
