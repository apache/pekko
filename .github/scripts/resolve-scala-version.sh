#!/usr/bin/env bash

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

set -euo pipefail

scala_version="${1:?scala version is required}"
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

resolve_from_build() {
  local pattern="$1"
  local resolved
  resolved="$(sed -n "$pattern" "$repo_root/project/Dependencies.scala")"
  if [ -z "$resolved" ]; then
    echo "Unable to resolve Scala version '$scala_version' from project/Dependencies.scala" >&2
    exit 1
  fi
  printf '%s\n' "$resolved"
}

case "$scala_version" in
  2.13 | 2.13.x | scala213)
    resolve_from_build 's/.*val scala213Version = "\(.*\)".*/\1/p'
    ;;
  3.3 | 3.3.x | scala3)
    resolve_from_build 's/.*val scala3Version = "\(.*\)".*/\1/p'
    ;;
  next)
    resolve_from_build 's/.*val scala3NextVersion = "\(.*\)".*/\1/p'
    ;;
  *)
    printf '%s\n' "$scala_version"
    ;;
esac
