//-----------------------------------------------------------------------------
//
// Copyright by the contributors to the Dafny Project
// SPDX-License-Identifier: MIT
//
//-----------------------------------------------------------------------------

using System.Diagnostics.Contracts;
using System.Numerics;

namespace Microsoft.Dafny.Compilers {
  public class JVerifyCodeGenerator : JavaCodeGenerator {
    public JVerifyCodeGenerator(DafnyOptions options, ErrorReporter reporter) : base(options, reporter) {
    }

    protected override string UnboundedIntRef => "Integer";
    protected override string DafnySetClass => "Set";
    protected override string DafnySeqClass => "List";
    protected override string DafnyMapClass => "Map";

    // Attempt to get everything in the same package, to avoid issues with imports
    // protected override string IdProtectModule(string moduleName)
    // {
    //   return string.Join("_", moduleName.Split(".").Select(IdProtect));
    // }

    protected override ConcreteSyntaxTree CreateMethod(MethodOrConstructor m, List<TypeArgumentInstantiation> typeArgs, bool createBody, ConcreteSyntaxTree wr,
      bool forBodyInheritance, bool lookasideBody)
    {
      if (m is Constructor c)
      {
        var cls = c.EnclosingClass;
        var javaName = IdName(cls);
        wr.Write($"public {javaName}(");
        WriteFormals("", m.Ins, wr);
        return wr.NewBlock(")", null, BlockStyle.NewlineBrace, BlockStyle.NewlineBrace);
      }
      return base.CreateMethod(m, typeArgs, createBody, wr, forBodyInheritance, lookasideBody);
    }

    protected override ConcreteSyntaxTree EmitMethodReturns(MethodOrConstructor m, ConcreteSyntaxTree wr) {
      int nonGhostOuts = 0;
      foreach (var t in m.Outs) {
        if (t.IsGhost) {
          continue;
        }

        nonGhostOuts += 1;
        break;
      }
      if (!m.Body.Body.OfType<ReturnStmt>().Any() && (nonGhostOuts > 0 || m.IsTailRecursive)) { // If method has out parameters or is tail-recursive but no explicit return statement in Dafny
        var fork = wr.Fork();
        EmitReturn(m.Outs, wr);
        return fork;
      }
      return wr;
    }

    protected override void DeclareField(string name, bool isStatic, bool isConst, Type type, IOrigin tok, string? rhs, ClassWriter cw)
    {
      if (isStatic) {
        var r = rhs ?? DefaultValue(type, cw.StaticMemberWriter, tok);
        var t = StripTypeParameters(TypeName(type, cw.StaticMemberWriter, tok));
        cw.StaticMemberWriter.WriteLine($"private static {t} {name} = {r};");
      } else {
        Contract.Assert(cw.CtorBodyWriter != null, "Unexpected instance field");
        cw.InstanceMemberWriter.WriteLine("private {0} {1};", TypeName(type, cw.InstanceMemberWriter, tok), name);
        cw.CtorBodyWriter.WriteLine("this.{0} = {1};", name, rhs ?? PlaceboValue(type, cw.CtorBodyWriter, tok));
      }
    }

    protected override string ActualTypeArgument(Type type, TypeParameter.TPVariance variance, ConcreteSyntaxTree wr, IOrigin tok)
    {
      return BoxedTypeName(type, wr, tok);
    }

    protected override void EmitStringLiteral(ConcreteSyntaxTree wr, StringLiteralExpr str)
    {
      wr.Write("\"" + str.AsStringLiteral() + "\"");
    }

    protected override void EmitUnboundedIntLiteral(ConcreteSyntaxTree wr, BigInteger i)
    {
      wr.Write(i.ToString());
    }

    protected override void TrExprAsInt(Expression expr, ConcreteSyntaxTree wr, bool inLetExprBody, ConcreteSyntaxTree wStmts,
      bool checkRange = false, string msg = null)
    {
      EmitExpr(expr, inLetExprBody, wr, wStmts);
    }

    protected override ConcreteSyntaxTree EmitDowncast(Type from, Type to, IOrigin tok, ConcreteSyntaxTree wr)
    {
      return wr;
      // return base.EmitDowncast(from, to, tok, wr);
    }

    protected override string IdName(IVariable v)
    {
      return v.Name;
    }

    protected override void EmitDatatypeValue(DatatypeValue dtv, string typeDescriptorArguments, string arguments, ConcreteSyntaxTree wr)
    {
      var ctor = dtv.Ctor;
      var dt = dtv.Ctor.EnclosingDatatype;
      var typeArgs = SelectNonGhost(dt, dtv.InferredTypeArgs);
      var typeParams = typeArgs.Count == 0 ? "" : $"<{BoxedTypeNames(typeArgs, wr, dt.Origin)}>";
      wr.Write($"new {DtCtorName(ctor)}{typeParams}({arguments})");
    }

    protected override ConcreteSyntaxTree CreateLambda(List<Type> inTypes, IOrigin tok, List<string> inNames, Type resultType, ConcreteSyntaxTree wr,
      ConcreteSyntaxTree wStmts, bool untyped = false)
    {
      var typedNames = inNames.Select((inName, i) => $"{TypeName(inTypes[i], wr, tok)} {inName}").ToList();
      wr.Write($"({typedNames.Comma(nm => nm)}) ->");
      var w = wr.NewExprBlock("");

      return w;
    }

    protected override void EmitMapDisplay(MapType mt, IOrigin tok, List<ExpressionPair> elements, bool inLetExprBody, ConcreteSyntaxTree wr,
      ConcreteSyntaxTree wStmts)
    {
      wr.Write($"{DafnyMapClass}.of(");
      string sep = "";
      foreach (ExpressionPair p in elements) {
        wr.Write(sep);
        wr.Append(Expr(p.A, inLetExprBody, wStmts));
        wr.Write(", ");
        wr.Append(Expr(p.B, inLetExprBody, wStmts));
        sep = ", ";
      }
      wr.Write(")");
    }

    protected override void GetSpecialFieldInfo(SpecialField.ID id, object idParam, Type receiverType, out string compiledName, out string preString,
      out string postString)
    {
      if (id == SpecialField.ID.UseIdParam)
      {
        if (((string)idParam).StartsWith("dtor_"))
        {
          compiledName = ((string)idParam).Substring(5);
          preString = "";
          postString = "";
          return;
        }
      }
      base.GetSpecialFieldInfo(id, idParam, receiverType, out compiledName, out preString, out postString);
    }

    protected override (Type, Action<ConcreteSyntaxTree>) EmitIntegerRange(Type type, Action<ConcreteSyntaxTree> wLo, Action<ConcreteSyntaxTree> wHi)
    {
      return (type, wr =>
      {
        var lo = new ConcreteSyntaxTree();
        wLo(lo);
        var hi = new ConcreteSyntaxTree();
        wHi(hi);
        wr.Write($"IntStream.range({lo},{hi})");
      });
    }

    protected override void CompileFunction(Function f, IClassWriter cw, bool lookasideBody)
    {
      var w = cw.CreateFunction(IdName(f), CombineAllTypeArguments(f),
        f.Ins, f.ResultType, f.Origin, f.IsStatic,
        !IsExternallyImported(f), f, false, lookasideBody);
      if (w != null) {
        IVariable accVar = null;
        Coverage.Instrument(f.Body.Origin, $"entry to function {f.FullName}", w);
        Contract.Assert(enclosingFunction == null);
        enclosingFunction = f;
        CompileReturnBody(f.Body, f.OriginalResultTypeWithRenamings(), w, accVar);
        Contract.Assert(enclosingFunction == f);
        enclosingFunction = null;
      }
    }

    public override bool HandleTailRecursion => false;

    protected override void EmitSeqBoundedPool(Expression of, bool includeDuplicates, string propertySuffix, bool inLetExprBody,
      ConcreteSyntaxTree wr, ConcreteSyntaxTree wStmts)
    {
      EmitExpr(of, inLetExprBody, wr, wStmts);
    }

    protected override void EmitAllocateClass(AssignmentRhs rhs, ConcreteSyntaxTree wr, ConcreteSyntaxTree wStmts, TypeRhs typeRhs,
      AllocateClass allocateClass)
    {      
      var constructor = allocateClass.InitCall?.Method as Constructor;
      var initCall = constructor != null ? allocateClass.InitCall : null;
      var type = allocateClass.Type;
      var tok = typeRhs.Origin;
      var ctor = (Constructor)initCall?.Method; // correctness of cast follows from precondition of "EmitNew"
      wr.Write($"new {TypeName(type, wr, tok)}(");
      var sep = "";
      if (type is UserDefinedType definedType) {
        var typeArguments = TypeArgumentInstantiation.ListFromClass(definedType.ResolvedClass, definedType.TypeArgs);
        EmitTypeDescriptorsActuals(typeArguments, tok, wr, ref sep);
      }
      
      var arguments = Enumerable.Empty<string>();
      if (ctor != null) {
        // the arguments of any external constructor are placed here
        arguments = ctor.Ins.Select((f, i) => (f, i))
          .Where(tp => !tp.f.IsGhost)
          .Select(tp => Expr(initCall.Args[tp.i], false, wStmts).ToString());
      }
      wr.Write((arguments.Any() ? sep : "") + arguments.Comma());
      wr.Write(")");
    }

    protected override void TrParenExpr(Expression expr, ConcreteSyntaxTree wr, bool inLetExprBody, ConcreteSyntaxTree wStmts)
    {
      EmitExpr(expr, inLetExprBody, wr, wStmts);
    }

    protected override void EmitLambdaExpr(bool inLetExprBody, ConcreteSyntaxTree wr, ConcreteSyntaxTree wStmts, LambdaExpr lambdaExpr)
    {
      IVariable receiver = null;
      var su = Substituter.EMPTY;
      if (receiver != null) {
        su = new Substituter(new IdentifierExpr(lambdaExpr.Origin, receiver), su.substMap, su.typeMap);
      }

      wr = CreateLambda(lambdaExpr.BoundVars.ConvertAll(bv => bv.Type), Token.NoToken, lambdaExpr.BoundVars.ConvertAll(IdName),
        lambdaExpr.Body.Type, wr, wStmts);
      wStmts = wr.Fork();
      wr = EmitReturnExpr(wr);
      // May need an upcast or boxing conversion to coerce to the generic arrow result type
      wr = EmitCoercionIfNecessary(lambdaExpr.Body.Type, TypeForCoercion(lambdaExpr.Type.AsArrowType.Result), lambdaExpr.Body.Origin, wr);
      EmitExpr(su.Substitute(lambdaExpr.Body), inLetExprBody, wr, wStmts);
    }

    protected override void EmitIdentifierExpr(bool inLetExprBody, ConcreteSyntaxTree wr, IdentifierExpr identifierExpr)
    {
      wr.Write(identifierExpr.Name);
    }

    protected override void EmitQuantifierExpr(bool inLetExprBody, ConcreteSyntaxTree wr, ConcreteSyntaxTree wStmts,
      QuantifierExpr e)
    {

      var isForall = e is ForallExpr;
      
      var i = 0;
      var bound = e.Bounds[i];
      var bv = e.BoundVars[i];

      var su = new Substituter(null, new Dictionary<IVariable, Expression>(), new Dictionary<TypeParameter, Type>());
      var collectionElementType = CompileCollection(bound, bv, inLetExprBody, false, su, out var collection,
        out var newtypeConversionsWereExplicit, wStmts,
        e.Bounds, e.BoundVars, i);
      
      collection(wr);
      wr.Write(".stream()");
      wr.Write(isForall ? ".allMatch(" : ".anyMatch(");
      wr.Write(TypeName(collectionElementType, wr, bv.Origin) + " ");
      wr.Write(bv.Name + " -> ");
      var ret = wr.Fork();
      wr.Write(")");
      
      var logicalBody = e.Term;
      EmitExpr(logicalBody, inLetExprBody, ret, wStmts);
    }

    protected override void EmitBinaryExpr(bool inLetExprBody, ConcreteSyntaxTree wr, ConcreteSyntaxTree wStmts, BinaryExpr binary)
    {
      
      string? opString = binary.ResolvedOp switch
      {
        BinaryExpr.ResolvedOpcode.Lt => "<",
        BinaryExpr.ResolvedOpcode.Gt => ">",
        BinaryExpr.ResolvedOpcode.Le => "<=",
        BinaryExpr.ResolvedOpcode.Ge => ">=",
        BinaryExpr.ResolvedOpcode.And => "&&",
        BinaryExpr.ResolvedOpcode.Or => "||",
        BinaryExpr.ResolvedOpcode.EqCommon => "==",
        BinaryExpr.ResolvedOpcode.NeqCommon => "!=",
        BinaryExpr.ResolvedOpcode.Add => "+",
        BinaryExpr.ResolvedOpcode.Sub => "-",
        BinaryExpr.ResolvedOpcode.Mul => "*",
        BinaryExpr.ResolvedOpcode.Div => "/",
        BinaryExpr.ResolvedOpcode.Mod => "%",
        BinaryExpr.ResolvedOpcode.Concat => "+",
        _ => null
      };

      if (opString == null)
      {
        base.EmitBinaryExpr(inLetExprBody, wr, wStmts, binary);
        return;
      }
      EmitExpr(binary.E0, inLetExprBody, wr, wStmts);
      wr.Write($" {opString} ");
      EmitExpr(binary.E1, inLetExprBody, wr, wStmts);
    }

    protected override void EmitVarDeclStatement(ConcreteSyntaxTree wr, VarDeclStmt declStmt)
    {
      if (declStmt.Locals.Count == 1 && declStmt.Assign is AssignStatement assignStatement &&
          assignStatement.Rhss.Count == 1)
      {
        wr.Write($"var ");
        TrStmt(declStmt.Assign, wr);
      }
      else
      {
        base.EmitVarDeclStatement(wr, declStmt);
      }
    }

    protected override string InternalFieldPrefix => "";

    protected override string SeqTypename(ConcreteSyntaxTree wr, IOrigin tok, bool erased, SeqType seqType)
    {
      if (seqType.TypeArgs[0] is CharType)
      {
        return "String";
      }
      return base.SeqTypename(wr, tok, erased, seqType);
    }

    protected override string FullTypeName(UserDefinedType udt, MemberDecl member, bool useCompanionName)
    {
      if (udt.IsBuiltinArrowType) {
        return DafnyFunctionIface(udt.TypeArgs.Count - 1);
      }

      if (member != null && member.IsExtern(Options, out var qualification, out _) && qualification != null) {
        return qualification;
      }
      var cl = udt.ResolvedClass;
      if (cl is NonNullTypeDecl nntd) {
        cl = nntd.Class;
      }
      if (cl is TypeParameter) {
        return IdProtect(udt.GetCompileName(Options));
      } else if (cl is TupleTypeDecl tupleDecl) {
        return DafnyTupleClass(tupleDecl.NonGhostDims);
      } else if (cl is TraitDecl && useCompanionName) {
        return IdProtect(udt.GetFullCompanionCompileName(Options));
      } else if (cl.EnclosingModuleDefinition.GetCompileName(Options) == ModuleName || cl.EnclosingModuleDefinition.TryToAvoidName) {
        return IdProtect(cl.GetCompileName(Options));
      } else {
        return //IdProtectModule(cl.EnclosingModuleDefinition.GetCompileName(Options)) + "." + 
               IdProtect(cl.GetCompileName(Options));
      }
    }

    protected override IClassWriter CreateClass(string moduleName, bool isExtern, string fullPrintName, List<TypeParameter> typeParameters, TopLevelDecl cls,
      List<Type> superClasses, IOrigin tok, ConcreteSyntaxTree wr)
    {
      superClasses = (cls as TopLevelDeclWithMembers)?.Traits ?? superClasses;
      
      var name = IdName(cls);
      var javaName = isExtern ? FormatExternBaseClassName(name) : name;
      var filename = $"{ModulePath}/{javaName}.java";
      var w = wr.NewFile(filename);
      w.WriteLine($"// Class {javaName}");
      w.WriteLine($"package {ModuleName};");
      w.WriteLine();
      var abstractness = isExtern ? "abstract " : "";
      w.Write($"public {abstractness}class {javaName}{TypeParameters(typeParameters)}");
      string sep;
      // Since Java does not support multiple inheritance, we are assuming a list of "superclasses" is a list of interfaces
      if (superClasses != null) {
        sep = " implements ";
        foreach (var trait in superClasses) {
          if (!trait.IsObject) {
            w.Write($"{sep}{TypeName(trait, w, tok)}");
            sep = ", ";
          }
        }
      }
      var wBody = w.NewBlock("");
      var wRestOfBody = wBody.Fork();
      return new ClassWriter(this, wRestOfBody, new ConcreteSyntaxTree());
    }

    protected override IClassWriter DeclareDatatype(DatatypeDecl dt, ConcreteSyntaxTree wr)
    {
      if (dt.IsRecordType)
      {
        return DeclareRecordDatatype(dt, wr);
      }
      return base.DeclareDatatype(dt, wr);
    }

    protected override string DtCtorName(DatatypeCtor ctor)
    {
      var dt = ctor.EnclosingDatatype;
      if (dt is TupleTypeDecl tupleDecl) {
        return DafnyTupleClass(tupleDecl.NonGhostDims);
      }
      var dtName = /*IdProtectModule(dt.EnclosingModuleDefinition.GetCompileName(Options)) + "." + */IdName(dt);
      return dt.IsRecordType ? dtName : dtName + "_" + ctor.GetCompileName(Options);
    }

    protected override void EmitTypeDescriptorsActuals(List<TypeArgumentInstantiation> typeArgs, IOrigin tok, ConcreteSyntaxTree wr, ref string prefix,
      bool useAllTypeArgs = false)
    {
    }

    protected override void EmitCardinality(Expression expr, bool inLetExprBody, ConcreteSyntaxTree wr, ConcreteSyntaxTree wStmts)
    {
      var collectionType = expr.Type.NormalizeToAncestorType().AsCollectionType;
      if (collectionType is MultiSetType)
      {
        throw new NotImplementedException();
      } else if (collectionType is SetType or MapType) {
        EmitExpr(expr, inLetExprBody, wr, wStmts);
        wr.Write(".size()");
      } else if (expr.Type.IsArrayType) {
        EmitExpr(expr, inLetExprBody, wr, wStmts);
        wr.Write(".length()");
      } else {
        EmitExpr(expr, inLetExprBody, wr, wStmts);
        wr.Write(".length()");
      }
    }

    private IClassWriter DeclareRecordDatatype(DatatypeDecl dt, ConcreteSyntaxTree wr)
    {
      var dtTTypeArgs = TypeParameters(dt.TypeArgs);
      var dtTProtected = IdName(dt) + dtTTypeArgs;

      var filename = $"{ModulePath}/{IdName(dt)}.java";
      wr = wr.NewFile(filename);
      wr.WriteLine($"package {ModuleName};");
      wr.WriteLine();
      
      // from here on, write everything into the new block created here:
      EmitSuppression(wr);
      wr.Write("public record {0}", dtTProtected);
      var superTraits = dt.Traits; //.ParentTypeInformation.UniqueParentTraits();

      var ctor = dt.Ctors[0];
      var parameterList = ctor.Formals.Where(f => !f.IsGhost).Select(
        (arg, i) => $"{TypeName(arg.Type, wr, arg.Origin)} {FieldName(arg, i)}");
      wr.Write($"({string.Join(", ", parameterList)})");
      
      if (superTraits.Any()) {
        wr.Write($" implements {superTraits.Comma(trait => TypeName(trait, wr, dt.Origin))}");
      }
      var btw = wr.NewBlock();
      wr = btw;

      return new ClassWriter(this, btw, ctorBodyWriter: null);
    }
  }
}
