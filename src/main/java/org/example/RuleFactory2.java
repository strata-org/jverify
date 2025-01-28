package org.example;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.codemodel.*;
import org.jsonschema2pojo.*;
import org.jsonschema2pojo.rules.ObjectRule;
import org.jsonschema2pojo.rules.Rule;
import org.jsonschema2pojo.rules.RuleFactory;
import org.jsonschema2pojo.util.NameHelper;
import org.jsonschema2pojo.util.ParcelableHelper;
import org.jsonschema2pojo.util.ReflectionHelper;

import java.util.Iterator;
import java.util.Map;

class RuleFactory2 extends RuleFactory {
    private RuleLogger logger;
    private NameHelper nameHelper;
    private ReflectionHelper reflectionHelper;
    private GenerationConfig generationConfig;
    private Annotator annotator;
    private SchemaStore schemaStore;

    public RuleFactory2(GenerationConfig generationConfig, Annotator annotator, SchemaStore schemaStore) {
        super(generationConfig, annotator, schemaStore);

        this.generationConfig = generationConfig;
        this.annotator = annotator;
        this.schemaStore = schemaStore;
        this.nameHelper = new NameHelper(generationConfig);
        this.reflectionHelper = new ReflectionHelper(this);
        this.logger = new NoopRuleLogger();
    }

    @Override
    public Rule<JPackage, JType> getObjectRule() {
        return new ObjectRule2(this, new ParcelableHelper(), this.reflectionHelper);
    }
}

class ObjectRule2 extends ObjectRule {

    private final RuleFactory ruleFactory
            ;

    protected ObjectRule2(RuleFactory ruleFactory, ParcelableHelper parcelableHelper, ReflectionHelper reflectionHelper) {
        super(ruleFactory, parcelableHelper, reflectionHelper);
        this.ruleFactory = ruleFactory;
    }

//"discriminator": {
//"propertyName": "$type",
//  "mapping": {
//    "addition": "#/definitions/Caddition",
//    "literal": "#/definitions/Citeral"
//}

//    @JsonTypeInfo(
//            use = JsonTypeInfo.Id.NAME,
//            include = JsonTypeInfo.As.PROPERTY,
//            property = "$type")
//    @JsonSubTypes({
//            @JsonSubTypes.Type(value = Caddition.class, name = "addition"),
//            @JsonSubTypes.Type(value = Citeral.class, name = "literal")
//    })
    @Override
    public JType apply(String nodeName, JsonNode node, JsonNode parent, JPackage _package, Schema schema) {
        JType result = super.apply(nodeName, node, parent, _package, schema);
        if (node.has("discriminator")) {
            JDefinedClass definedClass = (JDefinedClass)result;
            var typeInfo = definedClass.annotate(JsonTypeInfo.class);
            typeInfo.param("use", JsonTypeInfo.Id.NAME);
            typeInfo.param("include", JsonTypeInfo.As.PROPERTY);
            typeInfo.param("property", "$type");

            var subTypes = definedClass.annotate(JsonSubTypes.class);
            var typeArray = subTypes.paramArray("value");
            var inner = node.get("discriminator");
            var mapping = inner.get("mapping");
            for (Iterator<Map.Entry<String, JsonNode>> it = mapping.fields(); it.hasNext(); ) {
                var entry = it.next();
                var typeString = entry.getValue().asText();

                var objectNode = new ObjectNode(JsonNodeFactory.instance);
                objectNode.put("$ref", typeString);
                var childType = (JType)this.ruleFactory.getSchemaRule().apply("Unused", objectNode, node, _package, schema);

                JCodeModel model = new JCodeModel();
                JClass classRef = model.ref(childType.fullName());
                typeArray.annotate(JsonSubTypes.Type.class)
                        .param("value", classRef)
                        .param("name", entry.getKey());

            }
        }
        return result;
    }
}
