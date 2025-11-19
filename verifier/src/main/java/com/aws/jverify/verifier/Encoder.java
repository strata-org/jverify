package com.aws.jverify.verifier;

public interface Encoder {
    void writeBool(boolean value);

    void writeInt(int value);
    void writeLong(long value);

    void writeQualifiedName(String name);

    void writeString(String value);
    void writeNullable(boolean isNull);

    void writeDouble(double d);
}

class TextEncoder implements Encoder {
    StringBuilder writer;

    public TextEncoder(StringBuilder writer) {
        this.writer = writer;
    }

    @Override
    public void writeNullable(boolean isNull) {
        if (isNull) {
            writer.append("null");
            writer.append(' ');
        }
    }

    @Override
    public void writeDouble(double value) {
        var bigDecimal = new java.math.BigDecimal(Double.toString(value));
        writer.append(bigDecimal.toPlainString());
        writer.append(";");
        writer.append(' ');
    }

    @Override
    public void writeBool(boolean value) {
        writer.append(value ? "true" : "false");
        writer.append(' ');
    }

    @Override
    public void writeInt(int value) {
        writer.append(value);
        writer.append(";");
        writer.append(' ');
    }

    @Override
    public void writeLong(long value) {
        writer.append(value);
        writer.append(";");
        writer.append(' ');
    }

    @Override
    public void writeQualifiedName(String name) {
       writer.append(name);
       writer.append(' ');
    }

    @Override
    public void writeString(String value) {
        writer.append(escapeString(value));
        writer.append(' ');
    }
    
    private static String escapeString(String str) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : str.toCharArray()) {
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default: sb.append(c);
            }
        }
        sb.append("\"");
        return sb.toString();
    }
}


