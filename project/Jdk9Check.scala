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

import Jdk9.CompileJdk9
import sbt.AutoPlugin
import sbt.Def
import sbt.Keys._
import sbt._
import sbt.internal.BuildStructure

object Jdk9Check extends AutoPlugin {

  object autoImport {
    lazy val jdk9Check = taskKey[Unit]("Report which jars are in each scope.")
  }

  import autoImport._

  override lazy val trigger = allRequirements
  override lazy val requires = Jdk9
  val validScopeKey = (Compile / packageBin).scopedKey

  def scopedKeyMatch(scopedKey: ScopedKey[_], projectRef: ProjectRef): Boolean = {
    if (scopedKey.key != validScopeKey.key)
      return false
    if (scopedKey.scope.config != validScopeKey.scope.config) {
      return false
    }
    scopedKey.scope.project match {
      case Select(s) => s == projectRef
      case _         => false
    }
  }

  def hasJdk9Config(set: Set[_ <: sbt.ScopedKey[_]])(implicit cMap: Map[Def.ScopedKey[_], Def.Flattened]): Boolean = {
    var matchConfig = false
    for (elem <- set) {
      matchConfig = elem.scope.config match {
        case Select(s) => s.name == CompileJdk9.name
        case _         => false
      }
      if (matchConfig) {
        return true
      } else {
        val dd: Set[_ <: sbt.ScopedKey[_]] = cMap.get(elem) match {
          case Some(c) => c.dependencies.toSet;
          case None    => Set.empty
        }
        if (dd.nonEmpty) {
          matchConfig = hasJdk9Config(dd)
        }
      }
    }
    matchConfig
  }

  lazy val checkSettings = Seq(
    jdk9Check := {
      implicit val display = Project.showContextKey(state.value)
      val structure: BuildStructure = Project.extract(state.value).structure
      val currentProjectRef = thisProjectRef.value
      // Crawl all configurations of the current project
      val comp = Def.compiled(structure.settings, true)(structure.delegates, structure.scopeLocal, display)
      implicit val cMap = Def.flattenLocals(comp)
      // Filter: The packaging task of the current execution module
      val checkScopeKey = cMap.map(_._1).filter(sk => scopedKeyMatch(sk, currentProjectRef))
      // Dependency walking and check
      for (t <- checkScopeKey) {
        val dd = cMap.get(t) match {
          case Some(c) => c.dependencies.toSet;
          case None    => Set.empty
        }
        if (!hasJdk9Config(dd)) {
          // FIXME need change
          throw ScalaReflectionException("JDK9 not working")
        }
      }
    })

  override def projectSettings: Seq[Def.Setting[_]] = Jdk9.compileSettings ++ checkSettings

}
