package com.aws.jverify.verifier.compiler.simplifications;

import com.sun.tools.javac.code.Symbol;

import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Type.*;

import javax.lang.model.element.ElementKind;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Flow;

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
public class NameCompiler {
    public static final String sep = "?";
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

    private final Map<com.sun.tools.javac.util.Name, Integer> classNameOccurrenceCounts = new HashMap<>();
    private final Map<Symbol, String> symbolStringMap;
    private final Map<String, Symbol> reverseSymbolStringMap;
    private final ExternalContractCompiler contractCompiler;
    private final SimpleSynchronousPublisher<Symbol> subject = new SimpleSynchronousPublisher<>();
    
    Set<String> reservedDafnyNames = Set.of("map", "function", "set", "seq", "type", "method", "predicate", "this");
    
    public NameCompiler(ExternalContractCompiler contractCompiler) {
        this.contractCompiler = contractCompiler;
        this.symbolStringMap = new HashMap<>();
        this.reverseSymbolStringMap = new HashMap<>();
    }
    
    public Flow.Publisher<Symbol> foundSymbols() {
        return subject;    
    }
    
    public void registerClass(Symbol.ClassSymbol classSymbol) {
        classNameOccurrenceCounts.merge(classSymbol.name, 1, (a, b) -> a + 1);
    }

    public String safeGetOriginalName(String name) {
        if (reverseSymbolStringMap.containsKey(name)) {
            return reverseSymbolStringMap.get(name).name.toString();
        }
        return name;
    }
    
    public String getCompiledName(Symbol s) {
        if (symbolStringMap.containsKey(s)) {
            return symbolStringMap.get(s);
        }
        var compiledName = uncachedGetCompiledName(s);
        symbolStringMap.put(s, compiledName);
        var contractee = contractCompiler.contractClassToContractee.get(s);
        if (contractee == null) {
            subject.submit(s);
        }
        reverseSymbolStringMap.put(compiledName, s);
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
        var symtab = Symtab.instance(this.contractCompiler.compiler.context);
        if (classSymbol.type == symtab.objectType) {
            return ModifiableObjectCompiler.REFERENCE_OBJECT_NAME;
        }
        var newTarget = contractCompiler.contractClassToContractee.get(classSymbol);
        if (newTarget != null) {
            return uncachedGetCompiledName(newTarget);
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
        var classStats = getGetClassNameStats(s.enclClass(), s.name);
        if (classStats.methodsWithThisName > 0) {
            return fieldPrefix + s.name.toString();
        }
        return encodeName(s.name.toString());
    }

    private String getMethodName(Symbol.MethodSymbol s) {
        StringBuilder result = new StringBuilder();
                
        ClassNameStats classStats = getGetClassNameStats(s.enclClass(), s.name);
        boolean uniqueName = classStats.methodsWithThisName() <= 1;
        if (s.name.equals(s.name.table.names.init)) {
            result.append(getConstructorName(uniqueName));
        }
        else {
            if (classStats.sameNameFields()) {
                result.append(methodPrefix);
            }
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
        if (name.startsWith("_")) {
            name = UNDERSCORE_START_PREFIX + name;
        }
        return name.replace('$', '_');
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
                var newType = this.contractCompiler.contractClassTypeToContracteeType.get(type);
                if (newType != null) {
                    type = newType;
                }
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

    private ClassNameStats getGetClassNameStats(Symbol.ClassSymbol clazz, com.sun.tools.javac.util.Name name) {
        boolean sameNameFields = false;
        int methodsWithThisName = 0;
        var newClazz = this.contractCompiler.contractClassToContractee.get(clazz);
        if (newClazz == null) {
            newClazz = clazz;
        }
        for(var member : newClazz.members().getSymbolsByName(name)) {
            if (member instanceof Symbol.VarSymbol) {
                sameNameFields = true;
            }
            if (member instanceof Symbol.MethodSymbol) {
                methodsWithThisName += 1;
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
}
