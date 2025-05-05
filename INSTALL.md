JVerify can currently only be used by building it from source. 
 
- Once you have checked out the repository, make sure that all submodules have been checked out as well, which you can do using `git submodule update --init`.
- Ensure the dotnet runtime version 8 or higher is installed on your machine.
- Run `dotnet build dafny/Source/Dafny.sln` to build Dafny, a tool used by JVerify
- Install Z3 using one of the following make commands. Pick the one that matches your OS. Invoke `make -C dafny z3-mac/z3-mac-arm/z3-ubuntu`. For Windows you'll have to 

