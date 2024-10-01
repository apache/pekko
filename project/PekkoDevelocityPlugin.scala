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

import com.gradle.develocity.agent.sbt.DevelocityPlugin
import com.gradle.develocity.agent.sbt.DevelocityPlugin.autoImport.{
  develocityConfiguration,
  FlakyTestPolicy,
  ProjectId,
  Publishing
}
import sbt.{ inConfig, url, AutoPlugin, Def, PluginTrigger, Plugins, Setting }
import sbt.Keys.insideCI

object PekkoDevelocityPlugin extends AutoPlugin {

  private val ApacheDevelocityUrl = url("https://ge.apache.org")
  private val PekkoProjectId = ProjectId("pekko")
  private val ObfuscatedIPv4Address = "0.0.0.0"

  override lazy val trigger: PluginTrigger = allRequirements
  override lazy val requires: Plugins = DevelocityPlugin

  override lazy val buildSettings: Seq[Setting[_]] = Def.settings(
    develocityConfiguration := {
      val isInsideCI = insideCI.value

      val original = develocityConfiguration.value
      val apacheDevelocityConfiguration =
        original
          .withProjectId(PekkoProjectId)
          .withServer(
            original.server
              .withUrl(Some(ApacheDevelocityUrl))
              .withAllowUntrusted(false))
          .withBuildScan(
            original.buildScan
              .withPublishing(Publishing.onlyIf(_.authenticated))
              .withBackgroundUpload(!isInsideCI)
              .withObfuscation(
                original.buildScan.obfuscation
                  .withIpAddresses(_.map(_ => ObfuscatedIPv4Address))))
          .withBuildCache(
            original.buildCache
              .withLocal(
                original.buildCache.local
                  .withEnabled(false)))
      if (isInsideCI) {
        apacheDevelocityConfiguration
          .withTestRetryConfiguration(
            original.testRetryConfiguration
              .withMaxRetries(1)
              .withFlakyTestPolicy(FlakyTestPolicy.Fail) // preserve the original build outcome in case of flaky tests
          )
      } else apacheDevelocityConfiguration
    })
}

/**
 * An AutoPlugin to add Develocity test configuration to the TestJdk9 configuration.
 */
object PekkoDevelocityJdk9TestSettingsPlugin extends AutoPlugin {
  override lazy val trigger: PluginTrigger = allRequirements
  override lazy val requires: Plugins = DevelocityPlugin && Jdk9

  // See https://docs.gradle.com/develocity/sbt-plugin/#enabling_build_cache_in_a_custom_sbt_configuration
  override lazy val projectSettings = DevelocityPlugin.develocitySettings(Jdk9.TestJdk9)
}
