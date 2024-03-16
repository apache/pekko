#!/bin/bash

# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.

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
