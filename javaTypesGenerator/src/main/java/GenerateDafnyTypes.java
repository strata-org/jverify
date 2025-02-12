
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class GenerateDafnyTypes {

    public static void generateDafnyTypes() throws IOException {

        String input = Files.readString(Path.of("/Users/rwillems/SourceCode/dafny/Source/Scripts/bin/Debug/net8.0/parsedAst.cs"));
        new CSharpToJavaConverter("org.example.generated").
                writeJava(input, Paths.get("/Users/rwillems/SourceCode/GradleBased/src/main/java/org/example/generated"));

    }

}

