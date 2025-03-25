// See https://aka.ms/new-console-template for more information

using System.CommandLine;
using Microsoft.Dafny;
using RootCommand = Microsoft.Dafny.RootCommand;

var console = new WritersConsole(Console.In, Console.Out, Console.Error);
await RootCommand.Create().InvokeAsync(args, console);