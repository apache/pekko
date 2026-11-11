#!/usr/bin/env bash
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements. See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License. You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

set -euo pipefail

# Run with sbt on PATH, or pass an alternative launcher command as arguments.
# Example: scripts/check-organize-imports.sh java -jar /path/to/sbt-launch.jar
# Set SCALA_VERSION to exercise another supported Scala version.
root=$(cd "$(dirname "$0")/.." && pwd)
work=$(mktemp -d "${TMPDIR:-/tmp}/pekko-organize-imports.XXXXXX")
trap 'rm -rf "$work"' EXIT
mkdir -p "$work/project" "$work/src/main/scala"
cp "$root/project/build.properties" "$work/project/build.properties"
grep 'addSbtPlugin("ch.epfl.scala" % "sbt-scalafix"' "$root/project/plugins.sbt" > "$work/project/plugins.sbt"
cp "$root/.scalafix.conf" "$work/.scalafix.conf"
scala_version=${SCALA_VERSION:-$(sed -n 's/^  val scala213Version = "\([^"]*\)"/\1/p' "$root/project/Dependencies.scala")}
if [ -z "$scala_version" ]; then
  printf '%s\n' 'Could not determine the Scala 2.13 version from project/Dependencies.scala' >&2
  exit 1
fi
printf 'scalaVersion := "%s"\n' "$scala_version" > "$work/build.sbt"
cat >> "$work/build.sbt" <<'BUILD'
scalacOptions += "-Wunused:imports"
semanticdbEnabled := true
semanticdbVersion := scalafixSemanticdb.revision
BUILD
cat > "$work/src/main/scala/Dependencies.scala" <<'SCALA'
package org.apache.pekko { object Marker }
package example.library { object Library }
package org.slf4j { object Logger }
SCALA
cat > "$work/src/main/scala/Imports.scala" <<'SCALA'
package regression

import org.slf4j.Logger
import org.apache.pekko

import pekko.Marker
import example.library.Library
import scala.util.Try
import scala.concurrent.duration._
import java.util.UUID
import java.util.ArrayList

object Imports {
  val used = (Logger, Marker, Library, Try(UUID.randomUUID()), 1.second)
}
SCALA
cat > "$work/expected.txt" <<'SCALA'
package regression

import java.util.UUID

import scala.concurrent.duration._
import scala.util.Try

import example.library.Library

import org.apache.pekko
import pekko.Marker

import org.slf4j.Logger

object Imports {
  val used = (Logger, Marker, Library, Try(UUID.randomUUID()), 1.second)
}
SCALA
if [ "$#" -eq 0 ]; then
  set -- sbt
fi
cd "$work"
"$@" 'scalafix OrganizeImports'
diff -u expected.txt src/main/scala/Imports.scala
# Recompile the rewritten source and assert that a second pass makes no changes.
"$@" compile 'scalafix --check OrganizeImports'
printf '%s\n' 'OrganizeImports grouping, unused import removal, compilation and idempotence passed.'
