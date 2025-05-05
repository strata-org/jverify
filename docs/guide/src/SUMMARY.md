# Summary

[JVerify overview](./overview.md)

# Getting started

- [Getting started](./getting_started.md)

[//]: # (# Tutorial: Fundamentals)

[//]: # ()
[//]: # (- [Basic specifications]&#40;specs.md&#41;)

[//]: # (    - [assert, requires, ensures, ghost code]&#40;./requires_ensures.md&#41;)

[//]: # (    - [Expressions and operators for specifications]&#40;./operators.md&#41;)

[//]: # (    - [Integers and arithmetic]&#40;./integers.md&#41;)

[//]: # (    - [Equality]&#40;./equality.md&#41;)

[//]: # (- [Specification code, proof code, executable code]&#40;modes.md&#41;)

[//]: # (    - [spec functions]&#40;spec_functions.md&#41;)

[//]: # (    - [proof functions, proof blocks, assert-by]&#40;proof_functions.md&#41;)

[//]: # (    - [spec functions vs. proof functions, recommends]&#40;spec_vs_proof.md&#41;)

[//]: # (    - [Ghost code vs. exec code]&#40;ghost_vs_exec.md&#41;)

[//]: # (    - [const declarations]&#40;const.md&#41;)

[//]: # (    - [Putting it all together]&#40;triangle.md&#41;)

[//]: # (- [Recursion and loops]&#40;recursion_loops.md&#41;)

[//]: # (    - [Recursive spec functions, decreases, fuel]&#40;recursion.md&#41;)

[//]: # (    - [Recursive exec and proof functions, proofs by induction]&#40;induction.md&#41;)

[//]: # (        - [Lightweight termination checking]&#40;exec_termination.md&#41;)

[//]: # (    - [Loops and invariants]&#40;while.md&#41;)

[//]: # (        - [Loops with break]&#40;break.md&#41;)

[//]: # (        - [For Loops]&#40;for.md&#41;)

[//]: # (    - [Lexicographic decreases clauses and mutual recursion]&#40;lex_mutual.md&#41;)

[//]: # (- [Datatypes: struct and enum]&#40;datatypes.md&#41;)

[//]: # (    - [Struct]&#40;datatypes_struct.md&#41;)

[//]: # (    - [Enum]&#40;datatypes_enum.md&#41;)

[//]: # (- [Libraries]&#40;vstd.md&#41;)

[//]: # (    - [Specification libraries: Seq, Set, Map]&#40;spec_lib.md&#41;)

[//]: # (    - [Executable libraries: Vec]&#40;exec_lib.md&#41;)

[//]: # (- [Spec closures]&#40;spec_closures.md&#41;)

[//]: # ()
[//]: # (# Tutorial: Understanding the prover)

[//]: # ()
[//]: # (- [Developing proofs]&#40;develop_proofs.md&#41;)

[//]: # (    - [Using assert and assume]&#40;assert_assume.md&#41;)

[//]: # (    - [Devising loop invariants]&#40;invariants.md&#41;)

[//]: # (    - [Proving absence of overflow]&#40;overflow.md&#41;)

[//]: # (- [Quantifiers]&#40;quants.md&#41;)

[//]: # (    - [forall and triggers]&#40;forall.md&#41;)

[//]: # (    - [Multiple variables, multiple triggers, matching loops]&#40;multitriggers.md&#41;)

[//]: # (    - [exists and choose]&#40;exists.md&#41;)

[//]: # (    - [Proofs about forall and exists]&#40;quantproofs.md&#41;)

[//]: # (    - [Example: binary search]&#40;binary_search.md&#41;)

[//]: # (    - [Ambient &#40;`broadcast`&#41; lemmas]&#40;broadcast_proof.md&#41;)

[//]: # (- [SMT solving, automation, and where automation fails]&#40;smt_failures.md&#41;)

[//]: # (    - [What's decidable, what's undecidable, what's fast, what's slow]&#40;&#41; <!--- Chris --->)

[//]: # (    - [Integers and nonlinear arithmetic]&#40;nonlinear.md&#41;)

[//]: # (    - [Bit vectors and bitwise operations]&#40;bitvec.md&#41;)

[//]: # (    - [forall and exists: writing and using triggers, inline functions]&#40;&#41; <!--- Chris --->)

[//]: # (    - [Recursive functions]&#40;&#41; <!--- Chris --->)

[//]: # (    - [Extensional equality]&#40;extensional_equality.md&#41;)

[//]: # (    - [Libraries: incomplete axioms for Seq, Set, Map]&#40;&#41; <!--- Chris --->)

[//]: # (- [Managing proof performance and why it's critical]&#40;smt_perf_overview.md&#41;)

[//]: # (    - [Measuring verification performance]&#40;performance.md&#41;)

[//]: # (    - [Quantifier profiling]&#40;profiling.md&#41;)

[//]: # (    - [Modules, hiding, opaque, reveal]&#40;&#41; <!--- Chris --->)

[//]: # (    - [Hiding local proofs with `assert &#40;...&#41; by { ... }`]&#40;assert_by.md&#41;)

[//]: # (    - [Structured proofs by calculation]&#40;calc.md&#41;)

[//]: # (    - [Proof by computation]&#40;assert_by_compute.md&#41;)

[//]: # (    - [Spinning off separate SMT queries]&#40;&#41;)

[//]: # (    - [Breaking proofs into smaller pieces]&#40;breaking_proofs_into_pieces.md&#41;)

[//]: # (- [Checklist: what to do when proofs go wrong]&#40;checklist.md&#41;)

[//]: # ()
[//]: # (# Tutorial: Verification and Rust)

[//]: # ()
[//]: # (- [Mutation, references, and borrowing]&#40;&#41; <!--- Andrea --->)

[//]: # (    - [Requires and ensures with mutable references]&#40;&#41; <!--- Andrea --->)

[//]: # (    - [Assertions containing mutable references]&#40;&#41; <!--- Andrea --->)

[//]: # (- [Traits]&#40;&#41;)

[//]: # (- [Higher-order executable functions]&#40;./higher-order-fns.md&#41;)

[//]: # (    - [Passing functions as values]&#40;./exec_funs_as_values.md&#41;)

[//]: # (    - [Closures]&#40;./exec_closures.md&#41;)

[//]: # (- [Ghost and tracked variables]&#40;&#41;)

[//]: # (- [Strings]&#40;&#41; <!--- Andrea --->)

[//]: # (    - [String library]&#40;&#41; <!--- Andrea --->)

[//]: # (    - [String literals]&#40;&#41; <!--- Andrea --->)

[//]: # (- [Macros]&#40;&#41;)

[//]: # ()
[//]: # (- [Unsafe code & complex ownership]&#40;./complex_ownership.md&#41;)

[//]: # (  - [Cells / interior mutability]&#40;./interior_mutability.md&#41;)

[//]: # (  - [Pointers]&#40;./pointers.md&#41;)

[//]: # (  - [Concurrency]&#40;concurrency.md&#41;)

[//]: # ()
[//]: # (- [Verifying a container library: Binary Search Tree]&#40;./container_bst.md&#41;)

[//]: # (  - [First draft]&#40;./container_bst_first_draft.md&#41;)

[//]: # (  - [Encapsulating well-formedness with type invariants]&#40;./container_bst_type_invariant.md&#41;)

[//]: # (  - [Making it generic]&#40;./container_bst_generic.md&#41;)

[//]: # (  - [Implementing `Clone`]&#40;./container_bst_clone.md&#41;)

[//]: # (  - [Full source for the examples]&#40;./container_bst_all_source.md&#41;)

[//]: # ()
[//]: # (- [Interacting with unverified code]&#40;./interacting-with-unverified-code.md&#41;)

[//]: # (  - [Calling unverified code from verified code]&#40;./calling-unverified-from-verified.md&#41;)

[//]: # (  - [Calling verified code from unverified code]&#40;./calling-verified-from-unverified.md&#41;)

[//]: # ()
[//]: # (- [Understanding the guarantees of a verified program]&#40;./guarantees.md&#41;)

[//]: # (  - [Assumptions and trusted components]&#40;./tcb.md&#41;)

[//]: # (  - [Memory safety is conditional on verification]&#40;./memory-safety.md&#41;)

[//]: # (  - [Calling verified code from unverified code]&#40;./call-from-unverified-code.md&#41;)

[//]: # ()
[//]: # (# Installation, configuration, and tooling)

[//]: # ()
[//]: # (- [Installation and setup]&#40;&#41;)

[//]: # (  - [IDE Support]&#40;ide_support.md&#41;)

[//]: # (  - [Installing and configuring Singular]&#40;./install-singular.md&#41;)

[//]: # ()
[//]: # (- [Project setup and development]&#40;&#41;)

[//]: # (  - [Working with crates]&#40;&#41;)

[//]: # (  - [Invoking Verus code from Rust]&#40;&#41;)

[//]: # (  - [Documentation with Rustdoc]&#40;./verusdoc.md&#41;)

[//]: # ()
[//]: # (# Reference)

[//]: # ()
[//]: # (- [Supported and unsupported Rust features]&#40;./features.md&#41;)

[//]: # (- [Verus syntax by example]&#40;syntax.md&#41;)

[//]: # (- [Modes]&#40;&#41;)

[//]: # (  - [Function modes]&#40;&#41;)

[//]: # (  - [Variable modes]&#40;./reference-var-modes.md&#41;)

[//]: # (- [Spec expressions]&#40;./spec-expressions.md&#41;)

[//]: # (  - [Rust subset]&#40;./spec-rust-subset.md&#41;)

[//]: # (  - [Operator Precedence]&#40;./spec-operator-precedence.md&#41;)

[//]: # (  - [Arithmetic]&#40;./spec-arithmetic.md&#41;)

[//]: # (  - [Bit operators]&#40;./spec-bit-ops.md&#41;)

[//]: # (  - [Coercion with `as`]&#40;./reference-as.md&#41;)

[//]: # (  - [Spec equality &#40;`==`&#41;]&#40;./spec-equality.md&#41;)

[//]: # (  - [Extensional equality &#40;`=~=`, `=~~=`&#41;]&#40;./ref-extensional-equality.md&#41;)

[//]: # (  - [Prefix and/or &#40;&&& and |||&#41;]&#40;./prefix-and-or.md&#41;)

[//]: # (  - [Chained operators]&#40;./reference-chained-op.md&#41;)

[//]: # (  - [Implication &#40;`==>`, `<==`, and `<==>`&#41;]&#40;./reference-implication.md&#41;)

[//]: # (  - [Quantifiers &#40;`forall`, `exists`&#41;]&#40;./spec-quantifiers.md&#41;)

[//]: # (  - [Such that &#40;`choose`&#41;]&#40;./spec-choose.md&#41;)

[//]: # (  - [Trigger annotations]&#40;./trigger-annotations.md&#41;)

[//]: # (  - [The view function `@`]&#40;./reference-at-sign.md&#41;)

[//]: # (  - [Spec index operator `[]`]&#40;./reference-spec-index.md&#41;)

[//]: # (  - [`decreases_to!`]&#40;./reference-decreases-to.md&#41;)

[//]: # (- [Proof features]&#40;&#41;)

[//]: # (  - [assert and assume]&#40;&#41;)

[//]: # (  - [assert ... by]&#40;./reference-assert-by.md&#41;)

[//]: # (  - [assert forall ... by]&#40;./reference-assert-forall-by.md&#41;)

[//]: # (  - [assert ... by&#40;bit_vector&#41;]&#40;./reference-assert-by-bit-vector.md&#41;)

[//]: # (  - [assert ... by&#40;nonlinear_arith&#41;]&#40;./reference-assert-by-nonlinear.md&#41;)

[//]: # (  - [assert ... by&#40;compute&#41; / by&#40;compute_only&#41;]&#40;./reference-assert-by-compute.md&#41;)

[//]: # (  - [reveal, reveal_with_fuel, hide]&#40;./reference-reveal-hide.md&#41;)

[//]: # (- [Function specifications]&#40;&#41;)

[//]: # (  - [Function Signatures]&#40;&#41;)

[//]: # (    - [Exec fn signature]&#40;./reference-exec-signature.md&#41;)

[//]: # (    - [Proof fn signature]&#40;./reference-proof-signature.md&#41;)

[//]: # (    - [Spec fn signature]&#40;./reference-spec-signature.md&#41;)

[//]: # (  - [Signature clauses]&#40;&#41;)

[//]: # (    - [requires / ensures]&#40;&#41;)

[//]: # (    - [returns]&#40;./reference-returns.md&#41;)

[//]: # (    - [opens_invariants]&#40;./reference-opens-invariants.md&#41;)

[//]: # (    - [no_unwind]&#40;./reference-unwind-sig.md&#41;)

[//]: # (    - [recommends]&#40;./reference-recommends.md&#41;)

[//]: # (  - [Traits and signature inheritance]&#40;./reference-signature-inheritance.md&#41;)

[//]: # (  - [Specifications on FnOnce]&#40;./reference-signature-fnonce.md&#41;)

[//]: # (- [Loop specifications]&#40;&#41;)

[//]: # (  - [invariant]&#40;&#41;)

[//]: # (  - [invariant_except_break / ensures]&#40;&#41;)

[//]: # (- [Recursion and termination]&#40;&#41;)

[//]: # (  - [decreases ... when ... via ...]&#40;./reference-decreases.md&#41;)

[//]: # (  - [Datatype ordering]&#40;&#41;)

[//]: # (  - [Cyclic definitions]&#40;&#41;)

[//]: # (- [Type invariants]&#40;./reference-type-invariants.md&#41;)

[//]: # (- [Attribute list]&#40;./reference-attributes.md&#41;)

[//]: # (- [Directives]&#40;&#41;)

[//]: # (  - [`assume_specification`]&#40;./reference-assume-specification.md&#41;)

[//]: # (  - [`global`]&#40;./reference-global.md&#41;)

[//]: # (- [Misc. Rust features]&#40;&#41;)

[//]: # (  - [Statics]&#40;./static.md&#41;)

[//]: # (  - [char]&#40;./char.md&#41;)

[//]: # (  - [Unions]&#40;./reference-unions.md&#41;)

[//]: # (  - [Pointers and cells]&#40;./reference-pointers-cells.md&#41;)

[//]: # (- [Command line]&#40;&#41;)

[//]: # (  - [--record]&#40;./reference-flag-record.md&#41;)

[//]: # (- [Planned future work]&#40;&#41;)
