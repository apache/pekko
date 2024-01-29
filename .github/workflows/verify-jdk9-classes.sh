#!/bin/bash

echo "Checking Scala CLI version..."
scala-cli --version

echo "Starting verification with Scala-CLI..."

for file in stream/target/scala-cli/*.sc; do
  echo "Starting verification for with file: $file."
  if scala-cli "$file" ; then
    echo "Verification successful for $file."
  else
    echo "Error when verifying $file."
    exit 1
  fi
done

echo "All verifications successful."
