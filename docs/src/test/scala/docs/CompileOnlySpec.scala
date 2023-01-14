/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package docs

trait CompileOnlySpec {

  /**
   * Given a block of code... does NOT execute it.
   * Useful when writing code samples in tests, which should only be compiled.
   */
  def compileOnlySpec(body: => Unit) = ()
}
