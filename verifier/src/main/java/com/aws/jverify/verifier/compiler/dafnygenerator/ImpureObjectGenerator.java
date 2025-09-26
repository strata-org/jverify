package com.aws.jverify.verifier.compiler.dafnygenerator;

import com.aws.jverify.Impure;
import com.aws.jverify.generated.*;
import com.aws.jverify.verifier.compiler.dafnygenerator.base.BaseDafnyGenerator;
import com.aws.jverify.verifier.compiler.dafnygenerator.base.ExpressionCompiler;
import com.aws.jverify.verifier.compiler.dafnygenerator.base.ExpressionContext;
import com.aws.jverify.verifier.compiler.simplifications.NameCompiler;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.JCDiagnostic;
import com.aws.jverify.verifier.compiler.Reporter;
import com.sun.tools.javac.util.Context;

import java.util.stream.Stream;


/**
 * Verification types:
 *   trait Object
 *   trait ModifiableObject extends Object, object
 *
 * General transformations:
 *   Object -> Object
 *   @Impure Object -> ImpureObject
 *
 * Exceptional transformations:
 *   new Object -> new ImpureObject
 *   extends Object -> extends ImpureObject
 *
 */
public class ImpureObjectGenerator extends WrappingDafnyGenerator {
    public static final String IMPURE_OBJECT_NAME = "ImpureObject";

    private final Symtab symtab;
    private final Reporter reporter;
    private final NameCompiler nameCompiler;
    
    public ImpureObjectGenerator(Context context, BaseDafnyGenerator baseGenerator, DafnyGenerator original) {
        super(original);
        symtab = Symtab.instance(context);
        reporter = Reporter.instance(context);
        nameCompiler = NameCompiler.instance(context);
    }

    @Override
    public Type translateClassType(IOrigin origin, JCTree.JCModifiers additionalModifiers, com.sun.tools.javac.code.Type.ClassType classType) {
        var mirrors = classType.getAnnotationMirrors();
        var modifiableAnnotation = mirrors.stream().filter(t -> t.getAnnotationType().toString().equals(Impure.class.getName())).findFirst();
        if (modifiableAnnotation.isPresent() || BaseDafnyGenerator.isAnnotated(additionalModifiers, Impure.class)) {
            if (classType.tsym == symtab.objectType.tsym) {
                return new UserDefinedType(origin, new NameSegment(origin, IMPURE_OBJECT_NAME, null));
            } else {
                reporter.reportDiagnostic(origin, JCDiagnostic.DiagnosticType.WARNING, "notSupported", "@Impure on a type other than Object");
            }
        }
        if (classType.tsym == symtab.objectType.tsym) {
            return new UserDefinedType(origin, new NameSegment(origin, BaseDafnyGenerator.PURE_OBJECT_NAME, null));
        }
        return super.translateClassType(origin, additionalModifiers, classType);
    }

    public Type getRemappedType(com.sun.tools.javac.code.Type.ClassType classType, IOrigin origin, JCTree.JCModifiers additionalModifiers) {
        var mirrors = classType.getAnnotationMirrors();
        var modifiableAnnotation = mirrors.stream().filter(t -> t.getAnnotationType().toString().equals(Impure.class.getName())).findFirst();
        if (modifiableAnnotation.isPresent() || BaseDafnyGenerator.isAnnotated(additionalModifiers, Impure.class)) {
            if (classType.tsym == symtab.objectType.tsym) {
                return new UserDefinedType(origin, new NameSegment(origin, IMPURE_OBJECT_NAME, null));
            } else {
                reporter.reportDiagnostic(origin, JCDiagnostic.DiagnosticType.WARNING, "notSupported", "@Impure on a type other than Object");
            }
        }
        if (classType.tsym == symtab.objectType.tsym) {
            return new UserDefinedType(origin, new NameSegment(origin, BaseDafnyGenerator.PURE_OBJECT_NAME, null));
        }
        return null;
    }

    @Override
    public AssignmentRhs translateNewClassToAssignmentRhs(JCTree.JCNewClass newClass, IOrigin origin, ExpressionContext context) {
        if (newClass.type == symtab.objectType) {
            NameSegment classBaseType = new NameSegment(origin, 
                    nameCompiler.CLASS_PREFIX + IMPURE_OBJECT_NAME, null);

            String ctorNameStr = nameCompiler.getCompiledName(newClass.constructor, origin);
            Name ctorName = new Name(origin, ctorNameStr);
            var ty = new UserDefinedType(origin, new ExprDotName(origin, classBaseType, ctorName, null));

            var argBindings = ExpressionCompiler.createBindings(Stream.empty());
            return new AllocateClass(origin, null, ty, argBindings);
        }
        return super.translateNewClassToAssignmentRhs(newClass, origin, context);
    }
}
