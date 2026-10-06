# Java language reference tests

`corpus/` holds 1201 small Java programs, one construct each, derived from the
Java Language Specification SE 21. They were copied from the
`StrataJavaLangRefTests` package at commit `09163f8`; see that package's
`diff_tests/PROVENANCE.md` for how they were written and checked against `java`.

Each program's `run()` method is executed with
`--interpret --interpreter laurel --entry <Class>#run`, which runs the Laurel
interpreter directly instead of lowering to Core. `baseline.csv` records the
expected outcome of every program:

| outcome         | meaning                                                         |
|-----------------|-----------------------------------------------------------------|
| `passed`        | ran to completion, every `check` held                           |
| `assert_failed` | a `check` failed                                                |
| `refused`       | Strata declined to run it (a known limitation)                  |
| `interp_error`  | Strata exited non-zero (e.g. the interpreter lacks a construct) |
| `unsupported`   | the front end refused the program                               |
| `error`         | the front end reported some other error                         |
| `timeout`       | no result within `langref.timeoutSeconds` (default 30)          |

The suite fails when any outcome differs from the baseline, in either direction.
After an intended change, rewrite the baseline:

```
brazil-build :langref:test -Plangref.update=true
```

Every run writes `build/langref-results.csv` and the full output of each program
to `build/langref-output.log`.
