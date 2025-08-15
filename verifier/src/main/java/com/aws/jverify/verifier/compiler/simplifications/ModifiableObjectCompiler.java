package com.aws.jverify.verifier.compiler.simplifications;

import com.aws.jverify.Reference;
import com.aws.jverify.generated.IOrigin;
import com.aws.jverify.generated.NameSegment;
import com.aws.jverify.generated.Type;
import com.aws.jverify.generated.UserDefinedType;
import com.aws.jverify.verifier.compiler.JavaToDafnyCompiler;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.JCDiagnostic;

/**
 * Verification types:
 *   trait Object
 *   trait ModifiableObject extends Object, object
 * 
 * General transformations:
 *   Object -> Object
 *   @Modifiable Object -> ModifiableObject
 * 
 * Exceptional transformations:
 *   new Object -> new ModifiableObject
 *   extends Object -> extends ModifiableObject
 * 
 * Ideally this would be a separate pass that updates types in the AST
 */
public class ModifiableObjectCompiler {
    public static final String REFERENCE_OBJECT_NAME = "ModifiableObject";
    
    JavaToDafnyCompiler compiler;
    public final Context context;

    public ModifiableObjectCompiler(JavaToDafnyCompiler compiler) {
        this.compiler = compiler;
        context = compiler.context;
    }

    public Type getRemappedType(com.sun.tools.javac.code.Type.ClassType classType, IOrigin origin, JCTree.JCModifiers additionalModifiers) {
        Symtab symtab = Symtab.instance(context);
        
        var mirrors = classType.getAnnotationMirrors();
        var modifiableAnnotation = mirrors.stream().filter(t -> t.getAnnotationType().toString().equals(Reference.class.getName())).findFirst();
        if (modifiableAnnotation.isPresent() || compiler.isAnnotated(additionalModifiers, Reference.class)) {
            if (classType.tsym == symtab.objectType.tsym) {
                return new UserDefinedType(origin, new NameSegment(origin, REFERENCE_OBJECT_NAME, null));
            } else {
                compiler.reportDiagnostic(origin, JCDiagnostic.DiagnosticType.WARNING, "notSupported", "@Modifiable on a type other than Object");
            }
        }
        if (classType.tsym == symtab.objectType.tsym) {
            return new UserDefinedType(origin, new NameSegment(origin, JavaToDafnyCompiler.REFERENCE_OR_VALUE_OBJECT_NAME, null));
        }
        return null;
    }

    public NameSegment getNewClassType(JCTree.JCNewClass newClass) {
        var type = newClass.type;
        if (type == null) {
            // Workaround because we're still using the unlambda phase, which does not set newClass.type
            type = newClass.clazz.type;
        }
        var baseType = (UserDefinedType)compiler.translateType(type, compiler.toOrigin(newClass));
        var baseNameSegment = (NameSegment)baseType.getNamePath();
        var baseName = baseNameSegment.getName();
        if (baseName.contains(JavaToDafnyCompiler.REFERENCE_OR_VALUE_OBJECT_NAME)) {
            // 'new Object' should always create a Dafny ModifiableObject
            baseName = REFERENCE_OBJECT_NAME;
        }
        return new NameSegment(baseNameSegment.getOrigin(), compiler.nameCompiler.CLASS_PREFIX + baseName,
                baseNameSegment.getOptTypeArguments());
    }
}
