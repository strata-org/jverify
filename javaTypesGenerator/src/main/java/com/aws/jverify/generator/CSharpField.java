package com.aws.jverify.generator;

import java.util.ArrayList;
import java.util.List;

public class CSharpField {
    CSharpType type;
    String name;

    public CSharpField(CSharpType type, String name) {
        this.type = type;
        this.name = name;
    }
}
