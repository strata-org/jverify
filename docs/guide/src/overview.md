# JVerify
JVerify is a tool that helps ensure the correctness of Java programs. There are many existing tools for this. However, such tools are often either incomplete, not revealing bugs that are present, or inaccurate, reporting a problem when there is no bug.

JVerify is both complete and accurate. JVerify is complete because it allows the programmer to specify any type of behavior for their program, after which JVerify can ensure the implementation matches this. Typically desired behaviors, such as that the program does not throw any uncaught exceptions, or that the program must eventually terminate, do not need to be specified since JVerify knows about them already.

JVerify is accurate because when it is not sure that the program behaves according to a particular specification, it allows the programmer to provide hints, enabling it to decide whether there is a bug or not.

The specifications and hints used by JVerify are added to the program using regular Java calls to the JVerify library, enabling existing IDEs to be used.

To see examples of bugs that JVerify can detect, have a look at [this section](./example_usecases.md).

# Frequently asked questions

#### Can I use JVerify right now?
No, JVerify is not yet ready for production use. 

However, you can use the [installation](./installation.md) and [Running JVerify](./first_verification.md) sections in this guide to explore what it can currently do. Note that you will run into poor error messages, unsupported features and bugs - that are not yours ;-).

#### Does JVerify support the entire Java language?
Not yet, but it will support all Java language features. Note that we differentiate between Java the language, as documented in [its specification](https://docs.oracle.com/javase/specs/), and the Java standard library. More on JVerify's support for the latter is further down this FAQ.

Despite JVerify supporting all Java language features, there may be edge-cases where despite sufficient verification hints by the programmer, JVerify can not prove the correctness of a correct program. In these cases, you can either skip verification, or otherwise you will have to modify the program, even though it's already correct. One class of these edge-cases is where verification depends on information from the type system, and needs a downcast where the type system does not require one.

You can find the list of currently supported Java features here: [supported Java features](./supported_java_features.md), together with the known cases where JVerify can not verify a valid programs.

#### Can I gradually adopt JVerify in an existing codebase?
Yes, more about that is in [partially verifying a codebase](./partial_verification.md).

#### Can I use JVerify when calling libraries that were developed without JVerify?
Yes, more about that is in [adding contracts to third-party code](./external_contracts.md).
For the Java standard library, JVerify provides contracts for the most commonly used part of it out of the box. You can find which part of the standard library is supported in the section [supported Java features](./supported_java_features.md).

#### Which IDEs can I use with JVerify?
You can use any Java IDE when working with JVerify.

#### Will adding calls to the JVerify library slow down the execution of my program?
No, more about that is in [erasing verification code during compilation](./erase_verification.md). 

# This guide

This guide assumes that you're already familiar with Java. Specifications used by JVerify are written using regular Java expressions. However, verifying the correctness of Java code uses concepts that do not exist in regular Java, such as pre- and post-conditions. You can view JVerify as extending the Java language, even though it does not introduce any new syntax. This guide will walk you through the concepts that JVerify introduces.

JVerify uses a tool called a Satisfiability Modulo Theories (SMT) solver to help prove program correctness. Using this tool, JVerify will intelligently search through a space of proofs. For simple proofs, it can find them without help. For more complex proofs however, JVerify needs hints from you, the programmer. This guide will help you understand when JVerify does or does not need hints, and how to provide those.
