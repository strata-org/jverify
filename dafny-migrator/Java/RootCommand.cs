using System.CommandLine;
using Microsoft.Dafny.Compilers;
using Microsoft.Dafny.Plugins;

namespace Microsoft.Dafny;

static class RootCommand {

  public static IEnumerable<Option> Options =>
    new Option[] {
      CommonOptionBag.Output,
      IExecutableBackend.OuterModule,
      CommonOptionBag.IncludeRuntimeOption,
      RunAllTestsMainMethod.IncludeTestRunner,
      IExecutableBackend.TranslationRecordOutput
    }.Concat(DafnyCommands.TranslationOptions).
      Concat(DafnyCommands.ConsoleOutputOptions).
      Concat(DafnyCommands.ResolverOptions);
  
  public static Command Create() {
    var result = new System.CommandLine.RootCommand("Translate Dafny sources to JVerify sources");

    result.AddArgument(DafnyCommands.FilesArgument);
    foreach (var option in Options) {
      result.AddOption(option);
    }

    DafnyNewCli.SetHandlerUsingDafnyOptionsContinuation(result, async (options, context) =>
    {
      options.Compile = false;
      options.Backend = new JVerifyBackend(options);
      options.CompilerName = options.Backend.TargetId;
      var noVerify = options.Get(BoogieOptionBag.NoVerify);
      options.SpillTargetCode = noVerify ? 3U : 2U;
      return await SynchronousCliCompilation.Run(options);
    });

    return result;
  }
}
