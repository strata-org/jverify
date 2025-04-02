package com.aws.jverify.verifier;

import com.sun.tools.javac.api.Messages;

import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;

class JVerifyMessages implements Messages {

    Map<String, String> formatStrings = Map.of(
            "pureMethodMultipleStatements", "Pure method should have only one statement",
            "pureMethodsNeedsReturnType", "Pure method should have a return type",
            "pureMethodNeedsReturnStatement", "Pure method statement should be a return",
            "multipleReturnNames", "Ensures clauses may introduce only one return variable name",
            "argumentMustBeLambda", "The argument to a %s call must be a lambda",
            "notSupported", "%s is not supported",
            "contractAfterBody", "Call to JVerify header method %s is not allowed after non-header statement",
            "wrongContract", "%s are not allowed in a %s containerName"
    );
    
    @Override
    public void add(String bundleName) throws MissingResourceException {
    }

    @Override
    public String getLocalizedString(Locale l, String key, Object... args) {
        return formatStrings.get(key).formatted(args);
    }
}
