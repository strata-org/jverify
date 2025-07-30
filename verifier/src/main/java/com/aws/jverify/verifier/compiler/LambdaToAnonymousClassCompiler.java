package com.aws.jverify.verifier.compiler;

import com.aws.jverify.generated.TypeParameterEqualitySupportValue;
import com.sun.source.util.ParameterNameProvider;
import com.sun.tools.javac.code.*;
import com.sun.tools.javac.tree.*;
import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;

import javax.lang.model.element.ElementKind;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

import static com.sun.tools.javac.code.Flags.FINAL;
import static com.sun.tools.javac.code.Flags.SYNTHETIC;

/**
 * Hypothetical transformation that converts JCTree.JCLambda nodes into 
 * anonymous class implementations. This represents the approach that was
 * considered but ultimately rejected in favor of invokedynamic.
 */
public class LambdaToAnonymousClassCompiler extends TreeTranslator {

    private final TreeMaker make;
    private final Names names;
    private final Symtab syms;
    private final Types types;
    private int lambdaCounter = 0;

    public LambdaToAnonymousClassCompiler(Context context) {
        this.make = TreeMaker.instance(context);
        this.names = Names.instance(context);
        this.syms = Symtab.instance(context);
        this.types = Types.instance(context);
    }

    @Override
    public void visitLambda(JCLambda lambda) {
        // Transform lambda expression into anonymous class instance creation
        JCNewClass anonymousClass = transformLambdaToAnonymousClass(lambda);
        result = anonymousClass;
    }

    private JCNewClass transformLambdaToAnonymousClass(JCLambda lambda) {
        List<JCExpression> capturedArgs = createCapturedArguments(lambda);
        // Get the functional interface type
        Type functionalInterface = lambda.type;
        Symbol.MethodSymbol samMethod = findSAMMethod(functionalInterface);

        // Create captured variable parameters for constructor

        JCModifiers modifiers = make.Modifiers(SYNTHETIC | FINAL);

        // TODO test for collisions
        Name append = names.lambda.append(names.fromString(lambda.pos + ""));
        
        // Create constructor if we have captured variables
        var classSymbol = new Symbol.ClassSymbol(modifiers.flags, append, functionalInterface.tsym);
        Type.ClassType classType = new Type.ClassType(functionalInterface.getEnclosingType(), List.nil(), classSymbol);
        classType.interfaces_field = List.of(functionalInterface);
        classSymbol.type = classType;
        classSymbol.members_field = Scope.WriteableScope.create(classSymbol);

        // Create the method implementation
        JCMethodDecl implMethod = createImplementationMethod(classSymbol, lambda, samMethod);

        List<JCVariableDecl> capturedParams = createCapturedParameters(lambda, classSymbol);
        JCMethodDecl constructor = createConstructor(classSymbol, capturedParams);
        //capturedParams.isEmpty() ? null : createConstructor(capturedParams);

        // Create field declarations for captured variables
        List<JCVariableDecl> capturedFields = createCapturedFields(classSymbol, lambda);

        // Build the class body
        java.util.List<JCTree> classBody = new ArrayList<>();
        classBody.addAll(capturedFields);
        if (constructor != null) {
            classBody.add(constructor);
        }
        classBody.add(implMethod);
        classSymbol.members().enter(implMethod.sym);
        classSymbol.members().enter(constructor.sym);

        // Create the anonymous class definition
        JCClassDecl classDef = make.ClassDef(
                modifiers,
                names.lambda,
                List.nil(),
                null,
                List.of(make.Type(functionalInterface)),
                List.from(classBody)
        );
        classDef.sym = classSymbol;
        classDef.type = classSymbol.type;

        // Create the new class expression
        JCNewClass result = make.NewClass(
                null, // enclosing
                List.<JCExpression>nil(), // type args
                make.Type(functionalInterface), // class type
                capturedArgs, // constructor args
                classDef // class body
        );
        result.type = functionalInterface;
        if (constructor != null) {
            result.constructor = constructor.sym;
        }
        return result;
    }

    private Symbol.MethodSymbol findSAMMethod(Type functionalInterface) {
        // Find the single abstract method in the functional interface
        for (Symbol member : functionalInterface.tsym.members().getSymbols()) {
            if (member instanceof Symbol.MethodSymbol) {
                Symbol.MethodSymbol method = (Symbol.MethodSymbol) member;
                if ((method.flags() & Flags.ABSTRACT) != 0 &&
                        !isObjectMethod(method)) {
                    return method;
                }
            }
        }
        throw new AssertionError("No SAM method found in " + functionalInterface);
    }

    private boolean isObjectMethod(Symbol.MethodSymbol method) {
        // Check if this is a method from java.lang.Object
        String name = method.name.toString();
        return "equals".equals(name) || "hashCode".equals(name) ||
                "toString".equals(name) || "clone".equals(name) ||
                "finalize".equals(name);
    }

    private List<JCVariableDecl> createCapturedParameters(JCLambda lambda, Symbol.ClassSymbol classSymbol) {
        java.util.List<JCVariableDecl> params = new ArrayList<>();
        Set<Symbol> captured = findCapturedVariables(lambda);

        for (Symbol sym : captured) {
            JCVariableDecl param = make.VarDef(
                    make.Modifiers(Flags.FINAL),
                    sym.name,
                    make.Type(sym.type),
                    null
            );
            param.type = sym.type;
            param.sym = new Symbol.VarSymbol(0, param.name, param.type, classSymbol);
            params.add(param);
        }

        return List.from(params);
    }

    private List<JCExpression> createCapturedArguments(JCLambda lambda) {
        java.util.List<JCExpression> args = new ArrayList<>();
        Set<Symbol> captured = findCapturedVariables(lambda);

        for (Symbol sym : captured) {
            args.add(make.Ident(sym));
        }

        return List.from(args);
    }

    private List<JCVariableDecl> createCapturedFields(Symbol.ClassSymbol classSymbol, JCLambda lambda) {
        java.util.List<JCVariableDecl> fields = new ArrayList<>();
        Set<Symbol> captured = findCapturedVariables(lambda);

        for (Symbol sym : captured) {
            // Create field with same name as captured variable
            JCVariableDecl field = make.VarDef(
                    make.Modifiers(Flags.PRIVATE | Flags.FINAL),
                    sym.name,
                    make.Type(sym.type),
                    null
            );
            field.type = sym.type;
            field.sym = new Symbol.VarSymbol(FINAL, field.name, field.type, classSymbol);
            fields.add(field);
            classSymbol.members().enter(field.sym);
        }

        return List.from(fields);
    }

    private JCMethodDecl createConstructor(Symbol.ClassSymbol classSymbol, List<JCVariableDecl> capturedParams) {
        JCModifiers modifiers = make.Modifiers(SYNTHETIC);
        Symbol.MethodSymbol methodSymbol = new Symbol.MethodSymbol(modifiers.flags, names.init, new Type.MethodType(
                capturedParams.map(vd -> vd.type),
                classSymbol.type,
                List.nil(), classSymbol
        ), classSymbol);
        
        // Create assignment statements for captured variables
        java.util.List<JCStatement> assignments = new ArrayList<>();

        Symbol.VarSymbol thisSymbol = new Symbol.VarSymbol(FINAL, names._this, classSymbol.type, methodSymbol);
        for (JCVariableDecl param : capturedParams) {
            // this.fieldName = paramName;
            JCIdent thisIdent = make.Ident(names._this);
            thisIdent.sym = thisSymbol;
            thisIdent.type = classSymbol.type;
            Symbol.VarSymbol fieldSym = new Symbol.VarSymbol(FINAL, param.name, param.type, methodSymbol);
            JCFieldAccess fieldAccess = make.Select(thisIdent, param.name);
            fieldAccess.type = param.type;
            fieldAccess.sym = fieldSym;
            
            JCIdent paramIdent = make.Ident(param.name);
            paramIdent.type = param.type;
            paramIdent.sym = fieldSym; // param.sym is null
            
            JCAssign assignment = make.Assign(fieldAccess, paramIdent);
            assignments.add(make.Exec(assignment));
            //param.sym.owner = methodSymbol;
        }

        JCBlock body = make.Block(0, List.from(assignments));

        JCMethodDecl result = make.MethodDef(
                modifiers, // package-private constructor
                names.init, // <init>
                null, // no return type for constructor
                List.<JCTypeParameter>nil(), // no type parameters
                capturedParams, // parameters
                List.<JCExpression>nil(), // no throws
                body, // constructor body
                null // no default value
        );
        result.sym = methodSymbol;
        return result;
    }

    private JCMethodDecl createImplementationMethod(Symbol.ClassSymbol classSymbol, JCLambda lambda, Symbol.MethodSymbol samMethod) {

        JCModifiers modifiers = make.Modifiers(Flags.PUBLIC | SYNTHETIC);
        Symbol.MethodSymbol methodSymbol = new Symbol.MethodSymbol(modifiers.flags, samMethod.name, new Type.MethodType(
                lambda.params.map(vd -> vd.type),
                samMethod.type.getReturnType(),
                List.nil(), classSymbol
        ), classSymbol);
        
        // Transform lambda body, replacing captured variable references with field accesses
        JCTree transformedBody = transformLambdaBody(classSymbol, methodSymbol, lambda.body, findCapturedVariables(lambda));

        // Handle expression vs block body
        JCBlock methodBody;
        if (lambda.getBodyKind() == JCLambda.BodyKind.EXPRESSION) {
            // For expression bodies, create a return statement
            JCReturn returnStmt = make.Return((JCExpression) transformedBody);
            methodBody = make.Block(0, List.of(returnStmt));
        } else {
            // For block bodies, use as-is
            methodBody = (JCBlock) transformedBody;
        }

        JCMethodDecl result = make.MethodDef(
                modifiers, // public method
                methodSymbol.name, // method name
                make.Type(samMethod.getReturnType()), // return type
                List.<JCTypeParameter>nil(), // no type parameters
                lambda.params, // use lambda parameters directly
                List.<JCExpression>nil(), // no throws (should handle properly)
                methodBody, // method body
                null // no default value
        );
        result.sym = methodSymbol;

        for(var param : lambda.params) {
            param.sym.owner = result.sym;
        }
        return result;
    }

    private JCTree transformLambdaBody(Symbol.ClassSymbol classSymbol,
                                       Symbol.MethodSymbol methodSymbol,
                                       JCTree body, Set<Symbol> capturedVars) {
        Symbol.VarSymbol thisSymbol = new Symbol.VarSymbol(FINAL, names._this, classSymbol.type, methodSymbol);
        
        // Create a transformer that replaces captured variable references with field accesses
        TreeTranslator bodyTransformer = new TreeTranslator() {
            @Override
            public void visitIdent(JCIdent ident) {
                if (capturedVars.contains(ident.sym)) {
                    // Replace captured variable reference with this.fieldName
                    JCIdent thisIdent = make.Ident(names._this);
                    thisIdent.sym = thisSymbol;
                    thisIdent.type = classSymbol.type;
                    JCFieldAccess select = make.Select(thisIdent, ident.name);
                    select.sym = new Symbol.VarSymbol(FINAL, ident.name,  ident.type, classSymbol);
                    select.type = ident.type;
                    result = select;
                } else {
                    result = ident;
                }
            }
        };

        return bodyTransformer.translate(body);
    }

    private Set<Symbol> findCapturedVariables(JCLambda lambda) {
        // This is a simplified version - a real implementation would need
        // to do proper scope analysis to find truly captured variables
        Set<Symbol> captured = new HashSet<>();

        TreeScanner captureScanner = new TreeScanner() {
            @Override
            public void visitIdent(JCIdent ident) {
                Symbol sym = ident.sym;
                if (sym != null && isFromEnclosingScope(sym, lambda)) {
                    captured.add(sym);
                }
                super.visitIdent(ident);
            }
        };

        captureScanner.scan(lambda.body);
        return captured;
    }

    private boolean isEffectivelyFinal(Symbol sym) {
        // Check if variable is effectively final
        // In a real implementation, this would track assignments
        
        return true;
//        return sym.isFinal();
//        (sym.flags() & Flags.FINAL) != 0 ||
//                sym.kind == ElementKind.PARAMETER ||
//                isLocalVariableNeverAssigned(sym);
    }

//    private boolean isLocalVariableNeverAssigned(Symbol sym) {
//        // Placeholder - real implementation would track assignments
//        return sym.kind == ElementKind.LOCAL_VARIABLE;
//    }

    private boolean isFromEnclosingScope(Symbol sym, JCLambda lambda) {
        // Check if the symbol is from an enclosing scope rather than lambda parameters
        for (JCVariableDecl param : lambda.params) {
            if (param.sym == sym) {
                return false; // It's a lambda parameter
            }
        }
        return true; // From enclosing scope
    }
}

/**
 * Example usage of the transformation:
 *
 * Original lambda:
 *   list.forEach(s -> System.out.println(s));
 *
 * Transformed to:
 *   list.forEach(new Consumer<String>() {
 *       public void accept(String s) {
 *           System.out.println(s);
 *       }
 *   });
 *
 * With captured variables:
 *   String prefix = "Hello ";
 *   list.forEach(s -> System.out.println(prefix + s));
 *
 * Transformed to:
 *   String prefix = "Hello ";
 *   list.forEach(new Consumer<String>() {
 *       private final String prefix;
 *
 *       Consumer(String prefix) {
 *           this.prefix = prefix;
 *       }
 *
 *       public void accept(String s) {
 *           System.out.println(this.prefix + s);
 *       }
 *   });
 */