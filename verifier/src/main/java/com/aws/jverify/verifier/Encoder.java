package com.aws.jverify.verifier;

public interface Encoder {
    void indent();
    void undent();
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
    int depth;

    public TextEncoder(StringBuilder writer) {
        this.writer = writer;
    }

    @Override
    public void indent() {
        depth++;
    }

    @Override
    public void undent() {
        depth--;
    }

    private void writeIndent() {
        for (int i = 0; i < depth; i++) {
            writer.append(" ");
        }
    }

    @Override
    public void writeNullable(boolean isNull) {
        if (isNull) {
            writeIndent();
            writer.append("null");
            writer.append('\n');
        }
    }

    @Override
    public void writeDouble(double value) {
        writeIndent();
        writer.append(value);
        writer.append('\n');
    }

    @Override
    public void writeBool(boolean value) {
        writeIndent();
        writer.append(value ? "true" : "false");
        writer.append('\n');
    }

    @Override
    public void writeInt(int value) {
        writeIndent();
        writer.append(value);
        writer.append('\n');
    }

    @Override
    public void writeLong(long value) {
        writeIndent();
        writer.append(value);
        writer.append('\n');
    }

    @Override
    public void writeQualifiedName(String name) {
        writeIndent();
        writer.append(name);
        writer.append('\n');
    }

    @Override
    public void writeString(String value) {
        writeIndent();
        writer.append(escapeString(value));
        writer.append('\n');
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


