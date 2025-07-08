package com.aws.jverify.generated;

// Generated FileHeader.java:
// Generated from C# class
import java.util.List;

public class FileHeader {
  private final String uri;

  private final Boolean isLibrary;

  private final List<TopLevelDecl> topLevelDecls;

  public FileHeader(String uri, Boolean isLibrary, List<TopLevelDecl> topLevelDecls) {
    this.uri = uri;
    this.isLibrary = isLibrary;
    this.topLevelDecls = topLevelDecls;
  }

  public String getUri() {
    return this.uri;
  }

  public Boolean getIsLibrary() {
    return this.isLibrary;
  }

  public List<TopLevelDecl> getTopLevelDecls() {
    return this.topLevelDecls;
  }
}
