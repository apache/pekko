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

package org.apache.pekko.serialization

import java.io.File
import java.net.URL

import org.apache.pekko.annotation.InternalApi

import com.typesafe.config.{
  Config,
  ConfigFactory,
  ConfigIncludeContext,
  ConfigIncluder,
  ConfigIncluderClasspath,
  ConfigIncluderFile,
  ConfigIncluderURL,
  ConfigObject,
  ConfigParseOptions
}

/**
 * INTERNAL API
 *
 * Parsing of HOCON that arrived in a message.
 *
 * HOCON `include` directives are resolved by the parser, not by `resolve()`, so parsing a
 * string with the default includer reads whatever it names: `include file(...)` and
 * `include classpath(...)` read from the local filesystem and classpath, and
 * `include url(...)` performs an outbound request. None of that belongs on a path whose
 * input came from a peer.
 *
 * Every serializer writes config with `ConfigRenderOptions.concise`, which renders JSON and
 * cannot produce an `include`, so dropping them costs a well-behaved sender nothing.
 */
@InternalApi private[pekko] object WireConfig {

  /**
   * Resolves every form of `include` to an empty object.
   *
   * All four interfaces have to be implemented: the parser dispatches `include file(...)`,
   * `include url(...)` and `include classpath(...)` to the typed methods and falls back to
   * its own default handling — which does read the resource — when the configured includer
   * does not implement the matching interface. Only bare `include "..."` goes to `include`.
   */
  private object NoIncludes
      extends ConfigIncluder
      with ConfigIncluderFile
      with ConfigIncluderURL
      with ConfigIncluderClasspath {

    private def empty: ConfigObject = ConfigFactory.empty().root()

    override def withFallback(fallback: ConfigIncluder): ConfigIncluder = this
    override def include(context: ConfigIncludeContext, what: String): ConfigObject = empty
    override def includeFile(context: ConfigIncludeContext, what: File): ConfigObject = empty
    override def includeURL(context: ConfigIncludeContext, what: URL): ConfigObject = empty
    override def includeResources(context: ConfigIncludeContext, what: String): ConfigObject = empty
  }

  private val parseOptions: ConfigParseOptions = ConfigParseOptions.defaults().setIncluder(NoIncludes)

  /**
   * Like `ConfigFactory.parseString`, but with `include` directives resolved to nothing.
   */
  def parseString(hocon: String): Config = ConfigFactory.parseString(hocon, parseOptions)
}
