JVerify can currently only be used by building it from source. 
 
- Once you have checked out the repository, make sure that all submodules have been checked out as well, which you can do using `git submodule update --init`.
- Ensure the dotnet runtime version 8 or higher is installed on your machine.
- Run `dotnet build dafny/Source/Dafny.sln` to build Dafny, a tool used by JVerify
- Install Z3 using `make -C dafny z3-<OS>`, where OS can be `mac`/`mac-arm`/`ubuntu`. Pick the one that matches your OS. For Windows you will have to:
  - Go to [the release of version 4.12.1](https://github.com/Z3Prover/z3/releases/tag/z3-4.12.1), download the zip according to your system.  
  - Unzip the content of the Z3 folder into dafny\Binaries\z3 so that dafny\Binaries\z3\bin\z3.exe exists.

