/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import sbt._
import sbt.Keys._
import sbt.plugins.JvmPlugin

object TestQuickUntilPassed extends AutoPlugin {
  override lazy val requires = JvmPlugin

  override lazy val trigger = allRequirements

  object autoImport {
    lazy val testQuickUntilPassed = inputKey[Unit]("runs testQuick continuously until it passes")
  }

  import autoImport._

  private def testQuickRecursive(input: String): Def.Initialize[Task[Unit]] = Def.taskDyn {
    (Test / testQuick).toTask(input).result.value match {
      case Inc(cause) =>
        testQuickRecursive(input)
      case Value(value) =>
        Def.task(())
    }
  }

  override lazy val projectSettings =
    testQuickUntilPassed := {
      // TODO Figure out a way to pass input from testQuickUntilPassed into testQuickRecursive
      testQuickRecursive("").value
    }

}
