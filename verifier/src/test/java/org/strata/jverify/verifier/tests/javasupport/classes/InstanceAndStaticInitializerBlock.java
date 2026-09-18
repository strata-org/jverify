package org.strata.jverify.verifier.tests.javasupport.classes;

import org.strata.jverify.testengine.JVerifyTest;
import org.strata.jverify.testengine.Blocker;

@JVerifyTest(expectedToFail = true, blockedBy = {Blocker.FRONTEND_DIAGNOSTIC}, exitCode = 2)
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
