#!/bin/bash

# Loop through all Java files in the current directory
for file in *.java; do
  # Check if the file already contains the package declaration
  if ! grep -q "^package com.aws.jverify.verifier.jml_tests;" "$file"; then
    # Create a temporary file with the package declaration and the original content
    echo "package com.aws.jverify.verifier.jml_tests;" > temp_file
    cat "$file" >> temp_file
    # Replace the original file with the new content
    mv temp_file "$file"
    echo "Added package declaration to $file"
  else
    echo "$file already has the package declaration"
  fi
done
