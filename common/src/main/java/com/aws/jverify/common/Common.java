package com.aws.jverify.common;

import java.net.URL;
import java.security.CodeSource;
import java.security.ProtectionDomain;

public class Common {
    public static final String JVERIFY_CLASS = com.aws.jverify.JVerify.class.getName();
    
    public static String getJarLocationForClass(Class<?> clazz) {
        var protectionDomain = clazz.getProtectionDomain();
        var codeSource = protectionDomain.getCodeSource();

        if (codeSource != null) {
            URL location = codeSource.getLocation();
            return location.toString();
        } else {
            throw new RuntimeException("Not supported");
        }
    }
}