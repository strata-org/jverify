# JVerify

JVerify is a tool that can detect most bugs in a Java program at compile-time. It uses computer-aided theorem proving to prove that the program never throws any uncaught exceptions. If you specify properties about how your code should behave, such as what states a field is allowed to be null in, it can prove that a program will satisfy those for any possible execution.

Program specifications are provided by making calls to the JVerify library. These calls will be removed during compilation, using a plugin for `javac`, so they will not have an effect at run-time. Java code that contains JVerify specifications is still regular Java code, so it can be developed using any Java IDE.

# This guide

This guide assumes that you're already familiar with Java. Specifications used by JVerify are written using regular Java expressions. However, verifying the correctness of Java code uses concepts that do not exist in regular Java, such as pre- and post-conditions. You can view JVerify as extending the Java language, even though it does not introduce any new syntax. This guide will walk you through the concepts that JVerify introduces.

JVerify uses a tool called a Satisfiability Modulo Theories (SMT) solver to help prove program correctness. Using this tool, JVerify will intelligently search through a space of proofs. For simple proofs, it can find them without help. For more complex proofs however, JVerify needs hints from you, the programmer. This guide will help you understand when JVerify does or does not need hints, and how to provide those.
