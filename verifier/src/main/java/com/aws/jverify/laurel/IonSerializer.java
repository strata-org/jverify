package com.aws.jverify.laurel;

import com.amazon.ion.*;
import com.amazon.ion.system.*;

public class IonSerializer {
    private final IonSystem ion;

    private static final java.util.Map<String, java.util.Map<String, String>> SEPARATORS = java.util.Map.ofEntries(
        java.util.Map.entry("Laurel.mapType", java.util.Map.ofEntries(java.util.Map.entry("keyType", "seq"), java.util.Map.entry("valueType", "seq"))),
        java.util.Map.entry("Laurel.compositeType", java.util.Map.ofEntries(java.util.Map.entry("name", "seq"))),
        java.util.Map.entry("Laurel.literalBool", java.util.Map.ofEntries(java.util.Map.entry("b", "seq"))),
        java.util.Map.entry("Laurel.int", java.util.Map.ofEntries(java.util.Map.entry("n", "seq"))),
        java.util.Map.entry("Laurel.real", java.util.Map.ofEntries(java.util.Map.entry("d", "seq"))),
        java.util.Map.entry("Laurel.string", java.util.Map.ofEntries(java.util.Map.entry("s", "seq"))),
        java.util.Map.entry("Laurel.optionalType", java.util.Map.ofEntries(java.util.Map.entry("varType", "seq"))),
        java.util.Map.entry("Laurel.optionalAssignment", java.util.Map.ofEntries(java.util.Map.entry("value", "seq"))),
        java.util.Map.entry("Laurel.varDecl", java.util.Map.ofEntries(java.util.Map.entry("name", "seq"), java.util.Map.entry("varType", "seq"), java.util.Map.entry("assignment", "seq"))),
        java.util.Map.entry("Laurel.call", java.util.Map.ofEntries(java.util.Map.entry("callee", "seq"), java.util.Map.entry("args", "commaSepList"))),
        java.util.Map.entry("Laurel.new", java.util.Map.ofEntries(java.util.Map.entry("name", "seq"))),
        java.util.Map.entry("Laurel.fieldAccess", java.util.Map.ofEntries(java.util.Map.entry("obj", "seq"), java.util.Map.entry("field", "seq"))),
        java.util.Map.entry("Laurel.identifier", java.util.Map.ofEntries(java.util.Map.entry("name", "seq"))),
        java.util.Map.entry("Laurel.parenthesis", java.util.Map.ofEntries(java.util.Map.entry("inner", "seq"))),
        java.util.Map.entry("Laurel.assign", java.util.Map.ofEntries(java.util.Map.entry("target", "seq"), java.util.Map.entry("value", "seq"))),
        java.util.Map.entry("Laurel.add", java.util.Map.ofEntries(java.util.Map.entry("lhs", "seq"), java.util.Map.entry("rhs", "seq"))),
        java.util.Map.entry("Laurel.sub", java.util.Map.ofEntries(java.util.Map.entry("lhs", "seq"), java.util.Map.entry("rhs", "seq"))),
        java.util.Map.entry("Laurel.mul", java.util.Map.ofEntries(java.util.Map.entry("lhs", "seq"), java.util.Map.entry("rhs", "seq"))),
        java.util.Map.entry("Laurel.div", java.util.Map.ofEntries(java.util.Map.entry("lhs", "seq"), java.util.Map.entry("rhs", "seq"))),
        java.util.Map.entry("Laurel.mod", java.util.Map.ofEntries(java.util.Map.entry("lhs", "seq"), java.util.Map.entry("rhs", "seq"))),
        java.util.Map.entry("Laurel.divT", java.util.Map.ofEntries(java.util.Map.entry("lhs", "seq"), java.util.Map.entry("rhs", "seq"))),
        java.util.Map.entry("Laurel.modT", java.util.Map.ofEntries(java.util.Map.entry("lhs", "seq"), java.util.Map.entry("rhs", "seq"))),
        java.util.Map.entry("Laurel.eq", java.util.Map.ofEntries(java.util.Map.entry("lhs", "seq"), java.util.Map.entry("rhs", "seq"))),
        java.util.Map.entry("Laurel.neq", java.util.Map.ofEntries(java.util.Map.entry("lhs", "seq"), java.util.Map.entry("rhs", "seq"))),
        java.util.Map.entry("Laurel.gt", java.util.Map.ofEntries(java.util.Map.entry("lhs", "seq"), java.util.Map.entry("rhs", "seq"))),
        java.util.Map.entry("Laurel.lt", java.util.Map.ofEntries(java.util.Map.entry("lhs", "seq"), java.util.Map.entry("rhs", "seq"))),
        java.util.Map.entry("Laurel.le", java.util.Map.ofEntries(java.util.Map.entry("lhs", "seq"), java.util.Map.entry("rhs", "seq"))),
        java.util.Map.entry("Laurel.ge", java.util.Map.ofEntries(java.util.Map.entry("lhs", "seq"), java.util.Map.entry("rhs", "seq"))),
        java.util.Map.entry("Laurel.and", java.util.Map.ofEntries(java.util.Map.entry("lhs", "seq"), java.util.Map.entry("rhs", "seq"))),
        java.util.Map.entry("Laurel.or", java.util.Map.ofEntries(java.util.Map.entry("lhs", "seq"), java.util.Map.entry("rhs", "seq"))),
        java.util.Map.entry("Laurel.andThen", java.util.Map.ofEntries(java.util.Map.entry("lhs", "seq"), java.util.Map.entry("rhs", "seq"))),
        java.util.Map.entry("Laurel.orElse", java.util.Map.ofEntries(java.util.Map.entry("lhs", "seq"), java.util.Map.entry("rhs", "seq"))),
        java.util.Map.entry("Laurel.implies", java.util.Map.ofEntries(java.util.Map.entry("lhs", "seq"), java.util.Map.entry("rhs", "seq"))),
        java.util.Map.entry("Laurel.strConcat", java.util.Map.ofEntries(java.util.Map.entry("lhs", "seq"), java.util.Map.entry("rhs", "seq"))),
        java.util.Map.entry("Laurel.not", java.util.Map.ofEntries(java.util.Map.entry("inner", "seq"))),
        java.util.Map.entry("Laurel.neg", java.util.Map.ofEntries(java.util.Map.entry("inner", "seq"))),
        java.util.Map.entry("Laurel.optionalTrigger", java.util.Map.ofEntries(java.util.Map.entry("trigger", "seq"))),
        java.util.Map.entry("Laurel.forallExpr", java.util.Map.ofEntries(java.util.Map.entry("name", "seq"), java.util.Map.entry("ty", "seq"), java.util.Map.entry("trigger", "seq"), java.util.Map.entry("body", "seq"))),
        java.util.Map.entry("Laurel.existsExpr", java.util.Map.ofEntries(java.util.Map.entry("name", "seq"), java.util.Map.entry("ty", "seq"), java.util.Map.entry("trigger", "seq"), java.util.Map.entry("body", "seq"))),
        java.util.Map.entry("Laurel.errorMessage", java.util.Map.ofEntries(java.util.Map.entry("msg", "seq"))),
        java.util.Map.entry("Laurel.optionalElse", java.util.Map.ofEntries(java.util.Map.entry("stmts", "seq"))),
        java.util.Map.entry("Laurel.ifThenElse", java.util.Map.ofEntries(java.util.Map.entry("cond", "seq"), java.util.Map.entry("thenBranch", "seq"), java.util.Map.entry("elseBranch", "seq"))),
        java.util.Map.entry("Laurel.assert", java.util.Map.ofEntries(java.util.Map.entry("cond", "seq"), java.util.Map.entry("errorMessage", "seq"))),
        java.util.Map.entry("Laurel.assume", java.util.Map.ofEntries(java.util.Map.entry("cond", "seq"))),
        java.util.Map.entry("Laurel.return", java.util.Map.ofEntries(java.util.Map.entry("value", "seq"))),
        java.util.Map.entry("Laurel.block", java.util.Map.ofEntries(java.util.Map.entry("stmts", "semicolonSepList"))),
        java.util.Map.entry("Laurel.invariantClause", java.util.Map.ofEntries(java.util.Map.entry("cond", "seq"))),
        java.util.Map.entry("Laurel.while", java.util.Map.ofEntries(java.util.Map.entry("cond", "seq"), java.util.Map.entry("invariants", "seq"), java.util.Map.entry("body", "seq"))),
        java.util.Map.entry("Laurel.forLoop", java.util.Map.ofEntries(java.util.Map.entry("init", "seq"), java.util.Map.entry("cond", "seq"), java.util.Map.entry("step", "seq"), java.util.Map.entry("invariants", "seq"), java.util.Map.entry("body", "seq"))),
        java.util.Map.entry("Laurel.parameter", java.util.Map.ofEntries(java.util.Map.entry("name", "seq"), java.util.Map.entry("paramType", "seq"))),
        java.util.Map.entry("Laurel.mutableField", java.util.Map.ofEntries(java.util.Map.entry("name", "seq"), java.util.Map.entry("fieldType", "seq"))),
        java.util.Map.entry("Laurel.immutableField", java.util.Map.ofEntries(java.util.Map.entry("name", "seq"), java.util.Map.entry("fieldType", "seq"))),
        java.util.Map.entry("Laurel.isType", java.util.Map.ofEntries(java.util.Map.entry("target", "seq"), java.util.Map.entry("typeName", "seq"))),
        java.util.Map.entry("Laurel.asType", java.util.Map.ofEntries(java.util.Map.entry("target", "seq"), java.util.Map.entry("typeName", "seq"))),
        java.util.Map.entry("Laurel.optionalExtends", java.util.Map.ofEntries(java.util.Map.entry("parents", "commaSepList"))),
        java.util.Map.entry("Laurel.composite", java.util.Map.ofEntries(java.util.Map.entry("name", "seq"), java.util.Map.entry("extending", "seq"), java.util.Map.entry("fields", "seq"))),
        java.util.Map.entry("Laurel.datatypeConstructorArg", java.util.Map.ofEntries(java.util.Map.entry("name", "seq"), java.util.Map.entry("argType", "seq"))),
        java.util.Map.entry("Laurel.datatypeConstructor", java.util.Map.ofEntries(java.util.Map.entry("name", "seq"), java.util.Map.entry("args", "commaSepList"))),
        java.util.Map.entry("Laurel.datatypeConstructorNoArgs", java.util.Map.ofEntries(java.util.Map.entry("name", "seq"))),
        java.util.Map.entry("Laurel.datatypeConstructorList", java.util.Map.ofEntries(java.util.Map.entry("constructors", "commaSepList"))),
        java.util.Map.entry("Laurel.datatype", java.util.Map.ofEntries(java.util.Map.entry("name", "seq"), java.util.Map.entry("constructors", "seq"))),
        java.util.Map.entry("Laurel.optionalReturnType", java.util.Map.ofEntries(java.util.Map.entry("returnType", "seq"))),
        java.util.Map.entry("Laurel.requiresClause", java.util.Map.ofEntries(java.util.Map.entry("cond", "seq"), java.util.Map.entry("errorMessage", "seq"))),
        java.util.Map.entry("Laurel.ensuresClause", java.util.Map.ofEntries(java.util.Map.entry("cond", "seq"), java.util.Map.entry("errorMessage", "seq"))),
        java.util.Map.entry("Laurel.modifiesClause", java.util.Map.ofEntries(java.util.Map.entry("refs", "commaSepList"))),
        java.util.Map.entry("Laurel.returnParameters", java.util.Map.ofEntries(java.util.Map.entry("parameters", "commaSepList"))),
        java.util.Map.entry("Laurel.optionalBody", java.util.Map.ofEntries(java.util.Map.entry("body", "seq"))),
        java.util.Map.entry("Laurel.procedure", java.util.Map.ofEntries(java.util.Map.entry("name", "seq"), java.util.Map.entry("parameters", "commaSepList"), java.util.Map.entry("returnType", "seq"), java.util.Map.entry("returnParameters", "seq"), java.util.Map.entry("requires", "seq"), java.util.Map.entry("ensures", "seq"), java.util.Map.entry("modifies", "seq"), java.util.Map.entry("body", "seq"))),
        java.util.Map.entry("Laurel.function", java.util.Map.ofEntries(java.util.Map.entry("name", "seq"), java.util.Map.entry("parameters", "commaSepList"), java.util.Map.entry("returnType", "seq"), java.util.Map.entry("returnParameters", "seq"), java.util.Map.entry("requires", "seq"), java.util.Map.entry("ensures", "seq"), java.util.Map.entry("modifies", "seq"), java.util.Map.entry("body", "seq"))),
        java.util.Map.entry("Laurel.constrainedType", java.util.Map.ofEntries(java.util.Map.entry("name", "seq"), java.util.Map.entry("valueName", "seq"), java.util.Map.entry("base", "seq"), java.util.Map.entry("constraint", "seq"), java.util.Map.entry("witness", "seq"))),
        java.util.Map.entry("Laurel.compositeCommand", java.util.Map.ofEntries(java.util.Map.entry("composite", "seq"))),
        java.util.Map.entry("Laurel.procedureCommand", java.util.Map.ofEntries(java.util.Map.entry("procedure", "seq"))),
        java.util.Map.entry("Laurel.datatypeCommand", java.util.Map.ofEntries(java.util.Map.entry("datatype", "seq"))),
        java.util.Map.entry("Laurel.constrainedTypeCommand", java.util.Map.ofEntries(java.util.Map.entry("ct", "seq"))));

    public IonSerializer(IonSystem ion) {
        this.ion = ion;
    }

    /** Serialize a node as a top-level command (no "op" wrapper). */
    public IonValue serializeCommand(Node node) {
        return serializeNode(node);
    }

    /** Serialize a node as an argument (with "op" wrapper). */
    public IonValue serialize(Node node) {
        return wrapOp(serializeNode(node));
    }

    private IonSexp serializeNode(Node node) {
        IonSexp sexp = ion.newEmptySexp();
        String opName = node.operationName();
        sexp.add(ion.newSymbol(opName));
        sexp.add(serializeSourceRange(node.sourceRange()));

        var fieldSeps = SEPARATORS.getOrDefault(opName, java.util.Map.of());
        for (var component : node.getClass().getRecordComponents()) {
            if (component.getName().equals("sourceRange")) continue;
            try {
                java.lang.Object value = component.getAccessor().invoke(node);
                String sep = fieldSeps.get(component.getName());
                sexp.add(serializeArg(value, sep, component.getType()));
            } catch (java.lang.Exception e) {
                throw new java.lang.RuntimeException("Failed to serialize " + component.getName(), e);
            }
        }
        return sexp;
    }

    private IonValue wrapOp(IonValue inner) {
        IonSexp sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("op"));
        sexp.add(inner);
        return sexp;
    }

    private IonValue serializeSourceRange(SourceRange sr) {
        if (sr.start() == 0 && sr.stop() == 0) {
            return ion.newNull();
        }
        IonSexp sexp = ion.newEmptySexp();
        sexp.add(ion.newInt(sr.start()));
        sexp.add(ion.newInt(sr.stop()));
        return sexp;
    }

    private IonValue serializeArg(java.lang.Object value, String sep, java.lang.Class<?> type) {
        if (value == null) {
            return serializeOption(java.util.Optional.empty());
        }
        if (value instanceof Node n) {
            return serialize(n);
        }
        if (value instanceof java.lang.String s) {
            return serializeIdent(s);
        }
        if (value instanceof java.math.BigInteger bi) {
            return serializeNum(bi);
        }
        if (value instanceof java.math.BigDecimal bd) {
            return serializeDecimal(bd);
        }
        if (value instanceof byte[] bytes) {
            return serializeBytes(bytes);
        }
        if (value instanceof java.lang.Boolean b) {
            return serializeBool(b);
        }
        if (value instanceof java.util.Optional<?> opt) {
            return serializeOption(opt);
        }
        if (value instanceof java.util.List<?> list) {
            return serializeSeq(list, sep != null ? sep : "seq");
        }
        throw new java.lang.IllegalArgumentException("Unsupported type: " + type);
    }

    private IonValue serializeIdent(java.lang.String s) {
        IonSexp sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("ident"));
        sexp.add(ion.newNull());
        sexp.add(ion.newString(s));
        return sexp;
    }

    private IonValue serializeNum(java.math.BigInteger n) {
        IonSexp sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("num"));
        sexp.add(ion.newNull());
        sexp.add(ion.newInt(n));
        return sexp;
    }

    private IonValue serializeDecimal(java.math.BigDecimal d) {
        IonSexp sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("decimal"));
        sexp.add(ion.newNull());
        sexp.add(ion.newDecimal(d));
        return sexp;
    }

    private IonValue serializeBytes(byte[] bytes) {
        IonSexp sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("bytes"));
        sexp.add(ion.newNull());
        sexp.add(ion.newBlob(bytes));
        return sexp;
    }

    private IonValue serializeBool(boolean b) {
        IonSexp inner = ion.newEmptySexp();
        inner.add(ion.newSymbol(b ? "Init.boolTrue" : "Init.boolFalse"));
        inner.add(ion.newNull());
        return wrapOp(inner);
    }

    private IonValue serializeOption(java.util.Optional<?> opt) {
        IonSexp sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol("option"));
        sexp.add(ion.newNull());
        if (opt.isPresent()) {
            sexp.add(serializeArg(opt.get(), null, opt.get().getClass()));
        }
        return sexp;
    }

    private IonValue serializeSeq(java.util.List<?> list, String sepType) {
        IonSexp sexp = ion.newEmptySexp();
        sexp.add(ion.newSymbol(sepType));
        sexp.add(ion.newNull());
        for (java.lang.Object item : list) {
            sexp.add(serializeArg(item, null, item.getClass()));
        }
        return sexp;
    }
}
