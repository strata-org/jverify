package org.strata.jverify.verifier.tests.javasupport.classes;

import org.strata.jverify.testengine.JVerifyTest;

@JVerifyTest(skip = "Strata: not yet supported", exitCode = 2)
public class InstanceAndStaticInitializerBlock {
    int x;
    static int y;
    
    {
//  ^ error: an initializer block is not supported    
      x = 3;  
    }
    
    static {
//  ^ error: an initializer block is not supported
        y = 2;
    }
}
