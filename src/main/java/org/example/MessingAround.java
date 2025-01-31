package org.example;


import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

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
