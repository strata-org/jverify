package com.aws.jverify.generated;

// Generated FileStart.java:
// Generated from C# class
import java.util.List;

public class FileStart {
  private final String uri;

  private final List<TopLevelDecl> topLevelDecls;

  public FileStart(String uri, List<TopLevelDecl> topLevelDecls) {
    this.uri = uri;
    this.topLevelDecls = topLevelDecls;
  }

  public String getUri() {
    return this.uri;
  }

  public List<TopLevelDecl> getTopLevelDecls() {
    return this.topLevelDecls;
  }
}
