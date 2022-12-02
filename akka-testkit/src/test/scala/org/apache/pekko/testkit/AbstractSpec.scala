/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.testkit

import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

// we could migrate PekkoSpec to extend this
abstract class AbstractSpec extends AnyWordSpecLike with Matchers with BeforeAndAfterEach
