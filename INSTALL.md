JVerify can currently only be used by building it from source. 

1. Ensure the dotnet runtime version 8 or higher is installed on your machine. More information can be found [here](https://dotnet.microsoft.com/en-us/).
1. Check out the JVerify repository and its submodules, for example using `git clone https://github.com/aws/jverify --recurse-submodules`.
1. Navigate to the JVerify repository directory.
1. Run `gradle installDist`
1. Run `dotnet build dafny/Source/Dafny.sln` to build Dafny, a tool used by JVerify.
1. Install Z3 using `make -C dafny z3-<OS>`, where `<OS>` can be `mac`, `mac-arm` or `ubuntu`. Pick the one that matches your OS. For systems that do not fit those categories, such as Windows, you will have to take these steps instead:
   1. Download the release of [Z3 version 4.12.1](https://github.com/Z3Prover/z3/releases/tag/z3-4.12.1) that matches your system.  
   1. Unzip the content of the Z3 folder into dafny\Binaries\z3 so that dafny\Binaries\z3\bin\z3.exe exists.

