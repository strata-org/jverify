package com.aws.jverify.verifier.compiler.simplifications;

import com.aws.jverify.Contract;
import com.aws.jverify.Nullable;
import com.aws.jverify.Union;
import com.aws.jverify.generated.BinaryExpr;
import com.aws.jverify.generated.BinaryExprOpcode;
import com.aws.jverify.generated.BoundVar;
import com.aws.jverify.generated.Expression;
import com.aws.jverify.generated.IOrigin;
import com.aws.jverify.generated.IdentifierExpr;
import com.aws.jverify.generated.Name;
import com.aws.jverify.generated.NameSegment;
import com.aws.jverify.generated.SubsetTypeDecl;
import com.aws.jverify.generated.SubsetTypeDeclWKind;
import com.aws.jverify.generated.TopLevelDecl;
import com.aws.jverify.generated.TraitDecl;
import com.aws.jverify.generated.Type;
import com.aws.jverify.generated.TypeAutoInitInfo;
import com.aws.jverify.generated.TypeParameter;
import com.aws.jverify.generated.TypeParameterCharacteristics;
import com.aws.jverify.generated.TypeParameterEqualitySupportValue;
import com.aws.jverify.generated.TypeTestExpr;
import com.aws.jverify.generated.UserDefinedType;
import com.aws.jverify.verifier.compiler.JavaToDafnyCompiler;
import com.aws.jverify.verifier.compiler.JavaViolationException;
import com.sun.tools.javac.code.Symbol;

import java.util.ArrayList;
import java.util.List;

public class UnionCompiler {
    final JavaToDafnyCompiler compiler;

    public UnionCompiler(final JavaToDafnyCompiler compiler) {
        this.compiler = compiler;
    }

    /**
     * Returns a list of declarations that includes the given declaration,
     * along with a subset type declaration if the given class is a {@link Union} interface.
     */
    public List<TopLevelDecl> compile(TopLevelDecl decl, Symbol.ClassSymbol classSymbol) {
        var decls = new ArrayList<TopLevelDecl>();
        decls.add(decl);

        if (decl instanceof TraitDecl && compiler.isAnnotated(classSymbol.type, Union.class)) {
            var origin = decl.getOrigin();
            var name = decl.getNameNode();
            var typeParameters = decl.getTypeArgs();
            var subsetType = buildUnionSubsetType(classSymbol, origin, name, typeParameters);
            if (subsetType != null) {
                decls.add(subsetType);
            }
        }
        return decls;
    }

    private @Nullable TopLevelDecl buildUnionSubsetType(
            Symbol.ClassSymbol classSymbol,
            IOrigin origin,
            Name name,
            List<TypeParameter> typeParameters
    ) {
        if (!isSealedInterface(classSymbol)) {
            compiler.reportError(origin, "unionMustBeSealedInterface");
            return null;
        }

        // Any @Contract class must implement the interface and so must appear in the permits clause,
        // but shouldn't be translated to a datatype constructor.
        var variantTypes = classSymbol.getPermittedSubclasses()
                .stream()
                .filter(type -> !compiler.isAnnotated(type, Contract.class))
                .toList();
        if (!variantTypes.stream().allMatch(compiler::isRecord)) {
            compiler.reportError(origin, "unionMustPermitRecords");
            return null;
        }

        var typeArgs = typeParameters.stream()
                .map(p -> (Type) new UserDefinedType(
                        p.getOrigin(),
                        new NameSegment(p.getOrigin(), p.getNameNode().getValue(), null)))
                .toList();
        var traitType = new UserDefinedType(origin, new NameSegment(origin, name.getValue(), typeArgs));

        var unionName = new Name(origin, compiler.nameCompiler.UNION_PREFIX + name.getValue());
        var varName = new Name(origin, "u");
        var subsetVar = new BoundVar(origin, varName, traitType, false);
        var subsetVarExpr = new IdentifierExpr(subsetVar.getOrigin(), varName.getValue());
        var subsetConstraint = variantTypes.stream()
                .<Expression>map(variantType -> {
                    var dafnyType = compiler.translateType(variantType, origin);
                    return new TypeTestExpr(origin, subsetVarExpr, dafnyType);
                })
                .reduce((a, b) -> new BinaryExpr(origin, BinaryExprOpcode.Or, a, b))
                .orElseThrow(JavaViolationException::new);

        var typeParamCharacteristics = new TypeParameterCharacteristics(
                TypeParameterEqualitySupportValue.Unspecified,
                TypeAutoInitInfo.MaybeEmpty,
                false
        );
        return new SubsetTypeDecl(origin, unionName, null,
                typeParameters, typeParamCharacteristics,
                subsetVar, subsetConstraint,
                SubsetTypeDeclWKind.OptOut, null);
    }

    private static boolean isSealedInterface(Symbol.ClassSymbol classSymbol) {
        return classSymbol.isInterface() && classSymbol.isSealed();
    }
}
