Regular Java phases:

 - INIT(0),
 - PARSE(1),
 - ENTER(2),
 - PROCESS(3),
 - ATTR(4),
 - FLOW(5),
 - TRANSTYPES(6),
 - TRANSPATTERNS(7),
 - UNLAMBDA(8),
 - LOWER(9),
 - GENERATE(10);

TRANSTYPES does type erasure, which we don't want.
UNLAMBDA transforms Lambdas to something JVM specific, which we don't want either.
LOWER generated some erased code. We've made small modification to lower so it generated un-erased code.

Therefore, our current pipeline looks like this:

Java to Java passes:
 - Phase 1-5 from the Java compiler
 - Custom: compile method references to lambdas
 - Custom: compile lambdas to local classes
 - Custom: rewrite switches to annotated if-statements
 - Modified LOWER phase (phase 9 from javac),
 - Custom: write annotated ifs back into switches

- One big Java to Dafny pass:
  - Do-while loop to while compiler
  - For loop to while compiler
  - Support packages/imports by qualifying class names 
  - Support method overloads by adding parameters types to method name 
  - Rename symbols to avoid Dafny keywords
  - Extract method and loop contracts
  - Extract verification expressions (forall/exists/old/...)

- Dafny-to-Dafny: support trait constructors through a companion class