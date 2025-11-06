package com.aws.jverify.verifier.compiler.simplifications;

import com.aws.jverify.ContractException;
import com.aws.jverify.Pure;
import com.aws.jverify.Verify;
import com.aws.jverify.generated.IOrigin;
import com.aws.jverify.generated.LiteralExpr;
import com.aws.jverify.verifier.compiler.JavaViolationException;
import com.aws.jverify.verifier.compiler.frontend.JVerifyIndex;
import com.sun.tools.javac.code.*;
import com.sun.tools.javac.model.JavacElements;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.*;

import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class JVerifyUtils {

    private final JavacElements elements;
    private final TreeMaker maker;
    private final Names names;
    private final Symtab symtab;
    private final Types types;

    public static JVerifyUtils instance(Context context) {
        JVerifyUtils instance = context.get(JVerifyUtils.class);
        if (instance == null) {
            instance = new JVerifyUtils(context);
        }
        return instance;
    }

    private JVerifyUtils(Context context) {
        context.put(JVerifyUtils.class, this);
        elements = JavacElements.instance(context);
        maker = TreeMaker.instance(context);
        names = Names.instance(context);
        symtab = Symtab.instance(context);
        types = Types.instance(context);
    }

    public JCTree.JCAnnotation getVerifyFalseAnnotation() {
        var verifySymbol = getVerifyClassSymbol();
        JCTree.JCIdent value = maker.Ident(names.fromString("value"));
        value.sym = symtab.booleanType.tsym;
        return maker.Annotation(maker.Ident(verifySymbol), List.of(
                maker.Assign(value, maker.Literal(false))));
    }

    public Symbol.ClassSymbol getPureClassSymbol() {
        return elements.getTypeElement(Pure.class.getCanonicalName());
    }
    
    public Symbol.ClassSymbol getVerifyClassSymbol() {
        return elements.getTypeElement(Verify.class.getCanonicalName());
    }

    public void addVerifyFalseToMethodSymbol(Symbol.MethodSymbol contractMethod, Symbol.MethodSymbol contractee) {
        ListBuffer<Attribute.Compound> newAnnotations = new ListBuffer<>();
        newAnnotations.addAll(contractMethod.getAnnotationMirrors());
        newAnnotations.add(getVerifyAnnotation());
        contractee.resetAnnotations();
        contractee.setDeclarationAttributes(newAnnotations.toList());
    }
    
    public Attribute.Compound getVerifyAnnotation() {
        ListBuffer<Pair<Symbol.MethodSymbol, Attribute>> values = new ListBuffer<>();

        Symbol.MethodSymbol valueMethod = null;
        for (Symbol member : getVerifyClassSymbol().members().getSymbols()) {
            if (member instanceof Symbol.MethodSymbol && member.name.toString().equals("value")) {
                valueMethod = (Symbol.MethodSymbol) member;
                break;
            }
        }

        if (valueMethod != null) {
            Attribute falseAttr = new Attribute.Constant(valueMethod.type, false);
            values.add(new Pair<>(valueMethod, falseAttr));
        }

        return new Attribute.Compound(
                getVerifyClassSymbol().type,
                values.toList()
        );
    }

    public JCTree.JCStatement contractThrow() {
        var contractSymbol = elements.getTypeElement(ContractException.class.getCanonicalName());
        return maker.Throw(maker.Ident(contractSymbol));
    }

    public boolean isPure(Symbol.MethodSymbol rider) {
        if (rider.getAnnotation(Pure.class) != null) {
            return true;
        }

        var riderClass = rider.enclClass();
        for (Type superType : types.closure(riderClass.type)) {
            if (superType.tsym != riderClass) {
                for (Symbol member : superType.tsym.members().getSymbolsByName(rider.name)) {
                    if (member instanceof Symbol.MethodSymbol ridee &&
                            elements.overrides(rider, ridee, ridee.enclClass())) {
                        return isPure(ridee);
                    }
                }
            }
        }
        
        return false;
    }


    public static Map<String, JCTree.JCAnnotation> getAnnotationsByName(JCTree.JCModifiers modifiers) {
        var classAnnotations = modifiers.getAnnotations();
        return classAnnotations.stream().collect(Collectors.toMap(
                (JCTree.JCAnnotation a) -> a.getAnnotationType().type.toString(),
                a -> a,
                (first, second) -> first));
    }

    public static Map<String, JCTree.JCExpression> getArguments(JCTree.JCAnnotation annotation) {
        var result = new HashMap<String, JCTree.JCExpression>();
        for(var argument : annotation.getArguments()) {
            if (argument instanceof JCTree.JCAssign assign &&
                    assign.lhs instanceof JCTree.JCIdent ident) {
                result.put(ident.name.toString(), assign.rhs);
            } else {
                throw new JavaViolationException();
            }
        }
        return result;
    }

    public static boolean isInterface(Symbol.ClassSymbol classDecl) {
        return (classDecl.flags() & Flags.INTERFACE) != 0;
    }

    private static boolean isAbstract(Symbol.ClassSymbol classDecl) {
        return (classDecl.flags() & Flags.ABSTRACT) != 0;
    }

    public static boolean isInterfaceOrAbstract(Symbol.ClassSymbol classDecl) {
        return isInterface(classDecl) || isAbstract(classDecl);
    }

    public static Object getLiteralValue(JCTree.JCExpression expression) {
        if (expression instanceof JCTree.JCLiteral literal) {
            return literal.getValue();
        } else {
            throw new JavaViolationException();
        }
    }


    public static Symbol.ClassSymbol getClassSymbol(Names names, JCTree.JCExpression valueArgument) {
        if (valueArgument == null) {
            return null;
        }
        if (valueArgument instanceof JCTree.JCFieldAccess fieldAccess) {
            if (fieldAccess.name.contentEquals(names._class)) {
                return getClassSymbol(names, fieldAccess.selected);
            }
            if (fieldAccess.sym instanceof Symbol.ClassSymbol classSymbol) {
                return classSymbol;
            }
        }
        if (valueArgument instanceof JCTree.JCIdent ident &&
                ident.sym instanceof Symbol.ClassSymbol classSymbol) {
            return classSymbol;
        } else {
            throw new JavaViolationException();
        }
    }

    public static boolean typeHasSource(JVerifyIndex index, Symbol.TypeSymbol typeSymbol) {
        return index.getTree(typeSymbol) != null;
    }

    private static boolean isEnum(com.sun.tools.javac.code.Type type) {
        if (type instanceof com.sun.tools.javac.code.Type.ClassType classType) {
            return classType.supertype_field != null &&
                    classType.supertype_field.tsym instanceof Symbol.ClassSymbol classSymbol &&
                    classSymbol.fullname.contentEquals("java.lang.Enum");
        }
        return false;
    }

    public static boolean isRecord(com.sun.tools.javac.code.Type type) {
        return type instanceof com.sun.tools.javac.code.Type.ClassType classType
                && (classType.asElement().flags() & Flags.RECORD) != 0;
    }

    static class NotImplementedException extends RuntimeException {
        public NotImplementedException(String message) {
            super(message);
        }
    }

    private boolean isNullable(JCTree.JCModifiers modifiers) {
        return isAnnotated(modifiers, com.aws.jverify.Nullable.class);
    }

    private boolean isNullable(com.sun.tools.javac.code.Type type) {
        return isAnnotated(type, com.aws.jverify.Nullable.class);
    }

    /**
     * Returns {@code true} if the given modifier tree contains an annotation of the given class.
     */
    public static boolean isAnnotated(JCTree.JCModifiers modifiers, Class<? extends Annotation> clazz) {
        return modifiers != null && modifiers.getAnnotations().stream().anyMatch(a ->
                TreeInfo.symbol(a.getAnnotationType()) instanceof Symbol symbol
                        && symbol.flatName().contentEquals(clazz.getName()));
    }

    /**
     * Returns {@code true} if the given type or any of its supertypes is annotated with the given annotation class.
     */
    public boolean isAnnotatedRecursive(com.sun.tools.javac.code.Type type, Class<? extends Annotation> clazz) {
        return types.closure(type).stream().anyMatch(t -> isAnnotated(t, clazz));
    }

    /**
     * Returns {@code true} if the given type is annotated with the given annotation class.
     */
    public static boolean isAnnotated(com.sun.tools.javac.code.Type type, Class<? extends Annotation> clazz) {
        var metadata = type.getMetadata(TypeMetadata.Annotations.class);
        // In some JDK distributions, this conditional is necessary to detect the annotation.
        if (metadata != null && metadata.annotationBuffer().stream()
                .anyMatch(s -> s.type.tsym.getQualifiedName().contentEquals(clazz.getName()))) {
            return true;
        }
        return type.getAnnotation(clazz) != null || type.tsym.getAnnotation(clazz) != null;
    }

    public static boolean isSynthetic(JVerifyIndex index,  JCTree methodNode, Symbol.MethodSymbol methodSymbol) {
        var containerPos = index.getTree(methodSymbol.enclClass()).pos;
        return methodNode.pos == containerPos;
    }

    public static boolean isSynthetic(long flags) {
        return (flags & Flags.SYNTHETIC) != 0;
    }

    public static boolean isStatic(JCTree.JCModifiers modifiers) {
        return (modifiers.flags & Flags.STATIC) != 0;
    }

    public static boolean isStatic(Symbol symbol) {
        return (symbol.flags() & Flags.STATIC) != 0;
    }

    public static LiteralExpr getReferenceHole(IOrigin origin) {
        // TODO should be a typeless 'hole' expression, but Dafny does not have that.
        return new LiteralExpr(origin, null);
    }
    public static LiteralExpr getHole(IOrigin origin) {
        // TODO should be a typeless 'hole' expression, but Dafny does not have that.
        return new LiteralExpr(origin, true);
    }

    public static boolean isEnum(JCTree.JCExpression selected) {
        if (selected instanceof JCTree.JCIdent jcIdent) {
            if (jcIdent.sym instanceof Symbol.ClassSymbol classSymbol) {
                return isEnum(classSymbol.type);
            }
        }
        return false;
    }

    public static boolean isConstructor(Symbol.MethodSymbol methodSymbol) {
        return methodSymbol.name == methodSymbol.name.table.names.init;
    }
}
