package com.aws.jverify.verifier.compiler.simplifications;

import com.aws.jverify.generated.*;
import com.aws.jverify.verifier.compiler.ClassCompiler;
import com.aws.jverify.verifier.compiler.JavaToDafnyCompiler;
import com.aws.jverify.verifier.compiler.MethodOrLoopContract;
import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.code.*;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.ListBuffer;
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

        com.sun.tools.javac.code.Type returnType = methodSymbol.getReturnType();
        if (methodSymbol.name == methodSymbol.name.table.names.init) {
            returnType = methodSymbol.owner.type;
        }
        new InplaceParallelTypeVisitor().visitType(interfaceMethodSymbol.type, methodSymbol.type);
        var methodTypeWithAnnotations = methodSymbol.type;
        
//        com.sun.tools.javac.code.Type.MethodType methodType = new com.sun.tools.javac.code.Type.MethodType(
//                methodSymbol.type.getParameterTypes(),
//                returnType, methodSymbol.getThrownTypes(), 
//                (Symbol.TypeSymbol) methodSymbol.getEnclosingElement());
//        
//        
//        var methodTypeWithAnnotations = mergeAnnotations(interfaceMethodSymbol.type, methodType, types);

        // TODO create a new method symbol instead of passing in a separate type.
        var newSymbol = new Symbol.MethodSymbol(interfaceMethodSymbol.flags() | SYNTHETIC, 
                interfaceMethodSymbol.name, 
                methodTypeWithAnnotations, interfaceMethodSymbol.owner);
        var methodDecl = new ClassCompiler(compiler).
                translateMethodOrLambda(source, maker.Modifiers(0), newSymbol,
                        methodTypeWithAnnotations, body, List.of(), contract);

        ListBuffer<com.sun.tools.javac.code.Type> from = new ListBuffer<>();
        ListBuffer<com.sun.tools.javac.code.Type> to = new ListBuffer<>();
        //types.adapt(interfaceMethodSymbol.type, methodTypeWithAnnotations, from, to);
        types.adapt(interfaceMethodSymbol.type.getReturnType(), methodTypeWithAnnotations.getReturnType(), from, to);
        
        com.sun.tools.javac.util.List<com.sun.tools.javac.code.Type> parameterTypes = interfaceMethodSymbol.type.getParameterTypes();
        for (int i = 0; i < parameterTypes.size(); i++) {
            var parameter = parameterTypes.get(i);
            var parameter2 = methodTypeWithAnnotations.getParameterTypes().get(i);
            types.adapt(parameter, parameter2, from, to);
        }
        
        // Add a wrapper datatype with that method declaration to the outer scope
        var datatypeName = "Lambda" + System.identityHashCode(dynamicMethodSymbol);
        var datatypeNameNode = new Name(origin, datatypeName);
        List<Formal> datatypeCtorParams = params.stream().map(p ->
                new Formal(origin, compiler.getName(p, p.name), compiler.translateType(p.type, origin), false, true,
                        null, null, false, false, false, null)).toList();
        var datatypeCtor = new DatatypeCtor(origin, datatypeNameNode, null, false, datatypeCtorParams);
        
        var instantiatedInterfaceType = types.subst(interfaceType, from.toList(), to.toList());
        var trait = compiler.translateType(instantiatedInterfaceType, origin);
        JavacTrees trees = JavacTrees.instance(compiler.context);
        List<TypeParameter> typeArgs = new ClassCompiler(compiler).translateTypeParameters(
                methodSymbol.enclClass().getTypeParameters().map(tps -> (JCTree.JCTypeParameter) trees.getTree(tps)));
        var datatypeDecl = new IndDatatypeDecl(origin, datatypeNameNode, null, typeArgs, List.of(methodDecl),
                List.of(trait), List.of(datatypeCtor), false);
        compiler.declarationsForFile.get(compiler.compilationUnit).add(datatypeDecl);

        // Produce the datatype constructor reference: LambdaX.LambdaX
        return new ExprDotName(origin, new NameSegment(origin, datatypeName, null), datatypeNameNode, null);
    }
    
    public Type mergeAnnotations(Type genericType, Type instantiatedType, Types types) {
        return new ParallelTypeVisitor().visit(genericType, instantiatedType);
    }

    private String originToIdentifierSuffix(IOrigin origin) {
        return switch (origin) {
            case TokenRangeOrigin tokenRangeOrigin -> tokenToIdentifierSuffix(tokenRangeOrigin.getStartToken()) + 
                "_" + 
                tokenToIdentifierSuffix(tokenRangeOrigin.getEndToken());
            default -> throw new IllegalStateException("Unexpected value: " + origin);
        };
    } 
    
    private String tokenToIdentifierSuffix(Token token) {
        return token.getLine() + "_" + token.getCol();
    }
}
