package com.aws.jverify.verifier.compiler.simplifications;

import com.aws.jverify.Nullable;
import com.aws.jverify.generated.IOrigin;
import com.aws.jverify.verifier.compiler.Reporter;
import com.aws.jverify.verifier.compiler.generator.dafny.ImpureObjectGenerator;
import com.sun.tools.javac.code.Symbol;

import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Type.*;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.model.JavacElements;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeScanner;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Name;

import javax.lang.model.element.ElementKind;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Set;

/**
 * Maps names of symbols (classes, methods, fields) to compiled names to ensure uniqueness at the Dafny level.
 * The name mapping works as follows:
 *   - For fields that conflict with method names: prefix "F_" is added
 *   - For methods that conflict with field names: prefix "Z_" is added
 *   - For constructors: prefix "_ctor_" followed by the class name is used
 *   - For overloaded methods: a suffix encoding parameter types is added (e.g. "_iCjava_lang_String")
 *   - For classes with name collisions: fully qualified name with dots replaced by underscores
 * 
 * This class maintains bidirectional mappings between original and compiled names,
 * allowing for name resolution in both directions.
 */
public class NameCompiler extends TreeScanner {
    public static final char sep = '?';
    static private final String fieldPrefix = "F" + sep;
    static private final String methodPrefix = "Z" + sep;
    public String DEFAULT_CTOR_NAME = "ctor" + sep;
    public static final String NON_DEFAULT_CTOR_NAME = "ctor" + sep;
    public static String RETURN_VARIABLE_NAME = "result" + sep;
    public String CLASS_PREFIX = "Constructable" + sep;
    public String INIT_METHOD_PREFIX = "init";
    public String LABEL_PREFIX = "g" + sep;
    public String UNDERSCORE_START_PREFIX = "a" + sep;
    public String RESERVED_PREFIX = "r" + sep;

    private final Map<Name, Integer> classNameOccurrenceCounts = new HashMap<>();
    private final Map<Symbol, String> symbolStringMap;
    private final Map<String, Symbol> reverseSymbolStringMap;
    private final Symtab symtab;
    private final Types types;
    private final JavacElements elements;
    final Reporter reporter;
    
    public record FoundSymbol(Symbol symbol, IOrigin origin) {}
    Set<String> reservedDafnyNames = Set.of("iterator", "string", "class", "map", "function", "set", "seq", "type", "method", "predicate", "this");
    
    protected static final Context.Key<NameCompiler> myKey = new Context.Key<>();
    public static NameCompiler instance(Context context) {
        NameCompiler instance = context.get(myKey);
        if (instance == null)
            instance = new NameCompiler(context);
        return instance;
    }
    
    private NameCompiler(Context context) {
        context.put(myKey, this);
        this.symtab = Symtab.instance(context);
        this.types = Types.instance(context);
        this.elements = JavacElements.instance(context);
        this.symbolStringMap = new HashMap<>();
        reporter = Reporter.instance(context);
        this.reverseSymbolStringMap = new HashMap<>();
    }

    @Override
    public void visitClassDef(JCTree.JCClassDecl tree) {
        classNameOccurrenceCounts.merge(tree.sym.name, 1, (a, b) -> a + 1);
        super.visitClassDef(tree);
    }
    
    public String safeGetOriginalName(String name) {
        if (reverseSymbolStringMap.containsKey(name)) {
            return reverseSymbolStringMap.get(name).name.toString();
        }
        return name;
    }
    
    public String getCompiledName(Symbol symbol, JCTree node) {
        return getCompiledName(symbol, reporter.toOrigin(node));
    }
    
    public String getCompiledName(Symbol symbol, IOrigin origin) {
        if (symbolStringMap.containsKey(symbol)) {
            return symbolStringMap.get(symbol);
        }
        var compiledName = uncachedGetCompiledName(symbol);
        symbolStringMap.put(symbol, compiledName);
        reverseSymbolStringMap.put(compiledName, symbol);
        return compiledName;
    }
    
    private String uncachedGetCompiledName(Symbol s) {
        switch (s) {
            case Symbol.ClassSymbol classSymbol -> {
                return getClassName(classSymbol);
            }
            case Symbol.MethodSymbol m -> {
                return getMethodName(m);
            }
            case Symbol.VarSymbol varSymbol -> {
                return getVariableName(s, varSymbol);
            }
            default -> {
                return s.name.toString();
            }
        }
    }

    private String getVariableName(Symbol s, Symbol.VarSymbol varSymbol) {
        if (varSymbol.getKind().isField()
                || varSymbol.getKind() == ElementKind.RECORD_COMPONENT) {
            return getFieldName(varSymbol);
        } else { // Local variable, do not change
            if (s.name.isEmpty()) {
                return "unnamed";
            }
            return encodeName(s.name.toString());
        }
    }

    private String getClassName(Symbol.ClassSymbol classSymbol) {
        if (classSymbol.type == symtab.objectType) {
            return ImpureObjectGenerator.IMPURE_OBJECT_NAME;
        }
        var occurrenceCount = classNameOccurrenceCounts.get(classSymbol.name);
        var hasEmptyDotName = classSymbol.isAnonymous();
        if (hasEmptyDotName || occurrenceCount == null || occurrenceCount > 1) {
            return encodeName(classSymbol.flatName().toString().replace(".", "_"));
        }
        return encodeName(classSymbol.name.toString());
    }

    private String getFieldName(Symbol.VarSymbol s) {
        if (s.name.contentEquals(s.name.table.names._this)) {
            return s.name.toString();
        }
        var classStats = getGetClassNameStats(s, null);
        if (classStats.methodsWithThisName > 0) {
            return fieldPrefix + s.name.toString();
        }
        return encodeName(s.name.toString());
    }

    private String getMethodName(Symbol.MethodSymbol s) {
        StringBuilder result = new StringBuilder();
                
        ClassNameStats classStats = getGetClassNameStats(s, s);
        boolean uniqueName = classStats.methodsWithThisName() <= 1;
        if (s.name.equals(s.name.table.names.init)) {
            result.append(getConstructorName(uniqueName));
        }
        else {
            result.append(encodeName(s.name.toString()));
        }
        if (uniqueName) {
            return result.toString();
        }
        addArgumentTypes(s, result);
        return result.toString();
    }

    private String encodeName(String name) {
        if (reservedDafnyNames.contains(name)) {
            name = RESERVED_PREFIX + name;
        }
        name = name.replace('$', '_');
        if (name.startsWith("_")) {
            name = UNDERSCORE_START_PREFIX + name;
        }
        return name;
    }
    
    private void addArgumentTypes(Symbol.MethodSymbol method, StringBuilder result) {
        List<Type> argTypes;
        if (method.type instanceof MethodType m) {
            argTypes = m.getParameterTypes();
        } else if (method.type instanceof DelegatedType dt) {
            argTypes = dt.getParameterTypes();
        } else {
            throw new IllegalArgumentException(method.toString());
        }
        if (!argTypes.isEmpty()) {
            result.append("_");
            for (var param : argTypes) {
                result.append(getShortTypeName(param));
            }
        }
    }

    private String getShortTypeName(com.sun.tools.javac.code.Type type) {
        switch (type.getTag()) {
            case VOID : {return "v";}
            case BYTE : {return "b";}
            case CHAR : {return "c";}
            case SHORT: {return "s";}
            case INT : {return "i";}
            case LONG : {return "l";}
            case FLOAT :{return "f";}
            case DOUBLE : {return "d";}
            case BOOLEAN: {return "z";}
            case CLASS: {
                String classTypeStr = type.tsym.toString();
                // Changing '.' to '_' so that the new name is a valid Dafny name
                // TODO: test this for more complicated class names, e.g. when JCTypeApply is supported
                return "C" + classTypeStr.replace('.','_');
            }
            case TYPEVAR: {
                return type.toString();
            }
            case ARRAY : {
                var arrayType = (ArrayType) type;
                var componentType = arrayType.getComponentType();
                return "A" + getShortTypeName(componentType);
            }
            default : {
                return type.toString();
            }
        }
    }

    private ClassNameStats getGetClassNameStats(Symbol classMember, Symbol.@Nullable MethodSymbol method) {
        var name = classMember.name;
        boolean sameNameFields = false;
        int methodsWithThisName = 0;
        var clazz = classMember.enclClass();
        for(var member : clazz.members().getSymbolsByName(name)) {
            if (member instanceof Symbol.VarSymbol) {
                sameNameFields = true;
            }
            if (member instanceof Symbol.MethodSymbol) {
                methodsWithThisName += 1;
            }
        }

        for (Type superType : types.closure(clazz.type)) {
            if (superType.tsym != clazz) {
                for (Symbol member : superType.tsym.members().getSymbolsByName(name)) {
                    if (method != null && member instanceof Symbol.MethodSymbol methodSymbol && 
                            elements.overrides(method, methodSymbol, (Symbol.ClassSymbol)methodSymbol.owner)) {
                        // method overrides do not cause name collisions
                        continue;
                    }
                    
                    if (member instanceof Symbol.VarSymbol) {
                        sameNameFields = true;
                    } 
                    if (member instanceof Symbol.MethodSymbol) {
                        methodsWithThisName += 1;
                    }
                }
            }
        }
        return new ClassNameStats(sameNameFields, methodsWithThisName);
    }

    private record ClassNameStats(boolean sameNameFields, int methodsWithThisName) {}

    public String getConstructorName(boolean nonDefaultConstructor) {
        return nonDefaultConstructor ? NON_DEFAULT_CTOR_NAME : DEFAULT_CTOR_NAME;
    }

    public String getInitMethodName(String className, String constructorName) {
        return constructorName.replace("ctor", INIT_METHOD_PREFIX) + className;
    }

    public com.aws.jverify.generated.Name getName(JCTree tree, Symbol symbol) {
        return reporter.getName(tree, getCompiledName(symbol, tree), symbol.name.length());
    }
}
