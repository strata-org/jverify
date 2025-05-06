# JVerify overview

JVerify is a tool allows verifying the correctness of Java programs.

Verification is static: it adds no run-time checks. Instead it uses computer-aided theorem proving to statically verify that executable Java code will always satisfy some user-provided specifications for all possible executions of the code.

Program specifications are provided by making calls to the JVerify library. These calls can be removed during compilation, using a plugin for `javac`, so they will not have an effect at run-time. Java code that contains JVerify specifications is still regular Java code, so it can be analyzed by any Java IDE.

# This guide

This guide assumes that you're already familiar with Java. Specifications used by JVerify are written using regular Java expressions. However, verifying the correctness of Java code uses concepts that do not exist in regular Java, such as pre- and post-conditions. You can view JVerify as extending the Java language, even though it does not introduce any new syntax. This guide will walk you through the concepts that JVerify introduces.

JVerify uses a tool called a Satisfiability Modulo Theories (SMT) solver to help prove program correctness. Using this tool, JVerify will intelligently search through a space of proofs. For simple proofs, it can find them without help. For more complex proofs however, JVerify needs hints from you, the programmer. This guide will help you understand when JVerify does or does not need hints, and how to provide those.
