package org.example;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JPackage;
import com.sun.codemodel.JType;
import org.jsonschema2pojo.*;
import org.jsonschema2pojo.rules.Rule;
import org.jsonschema2pojo.rules.RuleFactory;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {

    public static void main(String[] args) throws IOException {

        var bufferedWriter = Files.newBufferedWriter(Paths.get("/Users/rwillems/SourceCode/GradleBased/out/ast.java"));
        String input = Files.readString(Path.of("/Users/rwillems/SourceCode/dafny/Source/Scripts/bin/Debug/net8.0/parsedAst.cs"));
        CSharpToJavaConverter.writeJava(input, bufferedWriter);

        // var javaClass = new CSharpToJavaGenerator().generateFromFile(cSharpExample);
//        Files.write(Path.of("/Users/rwillems/SourceCode/GradleBased/out/fromC#.java"), new CSharpToJavaGenerator().generateJavaFile(javaClass).getBytes());
//
//        var p = new Caddition(3, new Citeral(1, 3), new Citeral(2, 2));
//        ObjectMapper mapper2 = new ObjectMapper();
//        var output = mapper2.writeValueAsString(p);
//
//
//        JCodeModel codeModel = new JCodeModel();
//
//        URL source = new URL("file:/Users/rwillems/SourceCode/dafny/Source/Scripts/bin/Debug/net8.0/out.C.jschema");
//
//        GenerationConfig config = new DefaultGenerationConfig() {
//            @Override
//            public boolean isGenerateBuilders() { // set config option by overriding method
//                return true;
//            }
//        };
//
//        SchemaMapper mapper = new SchemaMapper(new RuleFactory2(config, new Jackson2Annotator(config), new SchemaStore()), new SchemaGenerator());
//        mapper.generate(codeModel, "ClassName", "com.example", source);
//
//        Path required = Path.of("/Users/rwillems/SourceCode/GradleBased/out");
//        codeModel.build(required.toFile());
//
//        System.out.println("Hello world! " + required);
    }

    final static String cSharpExample = """
record Expression(int Position);
record Addition(Expression Left, Expression Right) : Expression;
record Literal(int Value) : Expression;
            """;

    final String usesExtends = """
  {
  "$schema": "http://json-schema.org/draft-04/schema#",
  "title": "Cexpression",
  "type": "object",
  "discriminator": {
    "propertyName": "$type",
    "mapping": {
      "addition": "#/definitions/Caddition",
      "literal": "#/definitions/Citeral"
    }
  },
  "x-abstract": true,
  "additionalProperties": false,
  "required": [
    "$type"
  ],
  "properties": {
    "Position": {
      "type": "integer",
      "format": "int32"
    },
    "$type": {
      "type": "string"
    }
  },
  "definitions": {
    "Caddition": {
      "allOf": [
        {
          "$ref": "#"
        },
        {
          "type": "object",
          "additionalProperties": false,
          "properties": {
            "Left": {
              "$ref": "#"
            },
            "Right": {
              "$ref": "#"
            }
          }
        }
      ]
    },
    "Citeral": {
      "allOf": [
        {
          "$ref": "#"
        },
        {
          "type": "object",
          "additionalProperties": false,
          "properties": {
            "Value": {
              "type": "integer",
              "format": "int32"
            }
          }
        }
      ]
    }
  }
}
""";

    final String jsonInput = """
    {
  "$schema": "http://json-schema.org/draft-07/schema#",
  "definitions": {
    "BaseVehicle": {
      "type": "object",
      "properties": {
        "id": {
          "type": "string"
        },
        "manufacturer": {
          "type": "string"
        },
        "modelYear": {
          "type": "integer"
        }
      },
      "required": ["id", "manufacturer"],
      "discriminator": {
        "propertyName": "type"
      }
    },
    "Car": {
      "allOf": [
        {
          "$ref": "#/definitions/BaseVehicle"
        },
        {
          "type": "object",
          "properties": {
            "numberOfDoors": {
              "type": "integer",
              "minimum": 2,
              "maximum": 5
            },
            "type": {
              "type": "string",
              "enum": ["CAR"]
            }
          },
          "required": ["numberOfDoors", "type"]
        }
      ]
    },
    "Motorcycle": {
      "allOf": [
        {
          "$ref": "#/definitions/BaseVehicle"
        },
        {
          "type": "object",
          "properties": {
            "engineDisplacement": {
              "type": "number",
              "description": "Engine size in cubic centimeters"
            },
            "hasABS": {
              "type": "boolean",
              "default": false
            },
            "type": {
              "type": "string",
              "enum": ["MOTORCYCLE"]
            }
          },
          "required": ["engineDisplacement", "type"]
        }
      ]
    },
    "Fleet": {
      "type": "object",
      "properties": {
        "name": {
          "type": "string"
        },
        "vehicles": {
          "type": "array",
          "items": {
            "$ref": "#/definitions/BaseVehicle"
          }
        }
      },
      "required": ["name", "vehicles"]
    }
  }
}
""";
}


class CProgram {
    public Cexpression Root;
}

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "$type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = Caddition.class, name = "addition"),
        @JsonSubTypes.Type(value = Citeral.class, name = "literal")
})
abstract class Cexpression {
    public int Position;

    protected Cexpression(int position) {
        Position = position;
    }
}

class Caddition extends Cexpression {
    public Cexpression Left;
    public Cexpression Right;

  public Caddition(int position, Cexpression left, Cexpression right) {
      super(position);
    Left = left;
    Right = right;
    }
}

class Citeral extends Cexpression {
    public int Value;

  public Citeral(int position, int value){
      super(position);
      Value = value;
    }
}